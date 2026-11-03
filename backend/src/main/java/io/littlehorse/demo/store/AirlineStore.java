package io.littlehorse.demo.store;

import io.littlehorse.demo.kafka.*;
import io.littlehorse.demo.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;

/** The scheduling system. Inventory and itinerary changes commit together under row locks. */
@ApplicationScoped
public class AirlineStore {
    final DataSource db;

    public record Passenger(String id, String name, String preferences, boolean allowOvernight) {}

    public record ScheduledFlight(FlightLeg flight, int seats) {}

    public AirlineStore(DataSource db) {
        this.db = db;
    }

    @FunctionalInterface
    interface Tx<T> {
        T run(Connection c) throws Exception;
    }

    <T> T transaction(Tx<T> work) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof RuntimeException r) throw r;
                throw new IllegalStateException(e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Scheduling database unavailable", e);
        }
    }

    static PreparedStatement sql(Connection c, String query, Object... params) throws SQLException {
        PreparedStatement s = c.prepareStatement(query);
        for (int i = 0; i < params.length; i++) s.setObject(i + 1, params[i]);
        return s;
    }

    public List<Passenger> passengers() {
        return transaction(c -> {
            List<Passenger> list = new ArrayList<>();
            try (var s = sql(c, "SELECT * FROM passengers ORDER BY id");
                    var rs = s.executeQuery()) {
                while (rs.next())
                    list.add(new Passenger(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("preferences"),
                            rs.getBoolean("allow_overnight")));
            }
            return list;
        });
    }

    public Passenger passenger(String id) {
        return passengers().stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Passenger not found"));
    }

    public void seedPassengers(List<Passenger> passengers) {
        transaction(c -> {
            for (var p : passengers)
                try (var s = sql(
                        c,
                        "INSERT INTO passengers VALUES (?,?,?,?) ON CONFLICT(id) DO NOTHING",
                        p.id(),
                        p.name(),
                        p.preferences(),
                        p.allowOvernight())) {
                    s.executeUpdate();
                }
            return null;
        });
    }

    public void seedFlights(List<ScheduledFlight> flights) {
        transaction(c -> {
            for (var item : flights) {
                var f = item.flight();
                try (var s = sql(
                        c,
                        "INSERT INTO flights(flight_key,flight_number,departure_date,origin,destination,departure,arrival,boarding_closes,seats) VALUES (?,?,?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?) ON CONFLICT(flight_key) DO NOTHING",
                        f.getFlightKey(),
                        f.getFlightNumber(),
                        f.getDate(),
                        f.getOrigin(),
                        f.getDestination(),
                        f.getDeparture(),
                        f.getArrival(),
                        f.getBoardingCloses(),
                        item.seats())) {
                    s.executeUpdate();
                }
            }
            return null;
        });
    }

    static FlightLeg leg(ResultSet rs) throws SQLException {
        return new FlightLeg(
                rs.getString("flight_key"),
                rs.getString("flight_number"),
                rs.getString("departure_date"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getTimestamp("departure").toInstant().toString(),
                rs.getTimestamp("arrival").toInstant().toString(),
                rs.getTimestamp("boarding_closes").toInstant().toString());
    }

    public List<FlightStatus> flights() {
        return transaction(c -> {
            List<FlightStatus> out = new ArrayList<>();
            try (var s = sql(c, "SELECT * FROM flights ORDER BY departure");
                    var rs = s.executeQuery()) {
                while (rs.next())
                    out.add(new FlightStatus(leg(rs), rs.getInt("delay_minutes"), rs.getLong("sequence")));
            }
            return out;
        });
    }

    public FlightStatus delay(String key, int minutes) {
        if (minutes < 0 || minutes > 1440) throw new BadRequestException("Delay must be between 0 and 1440 minutes");
        return transaction(c -> {
            try (var s = sql(
                            c,
                            "UPDATE flights SET delay_minutes=?, sequence=sequence+1 WHERE flight_key=? RETURNING *",
                            minutes,
                            key);
                    var rs = s.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Flight not found");
                return new FlightStatus(leg(rs), rs.getInt("delay_minutes"), rs.getLong("sequence"));
            }
        });
    }

    public BookedTrip trip(String id) {
        return transaction(c -> trip(c, id, false));
    }

    static BookedTrip trip(Connection c, String id, boolean lock) throws SQLException {
        try (var s = sql(c, "SELECT data FROM trips WHERE trip_id=?" + (lock ? " FOR UPDATE" : ""), id);
                var rs = s.executeQuery()) {
            return rs.next() ? Json.read(rs.getString(1), BookedTrip.class) : null;
        }
    }

    public void recordBookingRequest(String id, Object request) {
        transaction(c -> {
            try (var s = sql(
                    c,
                    "INSERT INTO booking_requests VALUES(?,?::jsonb) ON CONFLICT(id) DO NOTHING",
                    id,
                    Json.write(request))) {
                s.executeUpdate();
            }
            try (var s = sql(c, "SELECT data FROM booking_requests WHERE id=?", id);
                    var rs = s.executeQuery()) {
                rs.next();
                if (!Json.MAPPER.readTree(rs.getString(1)).equals(Json.MAPPER.valueToTree(request)))
                    throw new ClientErrorException("Idempotency-Key is already used by a different booking", 409);
            }
            return null;
        });
    }

    public ReservationRequest initial(String id, String passengerId, String[] numbers, String date) {
        passenger(passengerId);
        if (numbers == null || numbers.length == 0 || numbers.length > 3)
            throw new BadRequestException("Choose one to three flight numbers");
        Set<String> valid = new HashSet<>();
        for (var f : flights()) valid.add(f.flight().getFlightKey());
        String[] keys = Arrays.stream(numbers)
                .map(n -> n.toUpperCase(Locale.ROOT) + "-" + date)
                .toArray(String[]::new);
        for (String key : keys) if (!valid.contains(key)) throw new BadRequestException("Unknown flight " + key);
        return new ReservationRequest(id, passengerId, 0, keys, "");
    }

    public BookedTrip reserve(ReservationRequest request) {
        return transaction(c -> {
            // Advisory lock also serializes creation when no trip row exists yet.
            try (var s = sql(c, "SELECT pg_advisory_xact_lock(hashtext(?))", request.getTripId())) {
                s.execute();
            }
            try (var s = sql(
                            c,
                            "SELECT data FROM reservation_results WHERE trip_id=? AND expected_revision=?",
                            request.getTripId(),
                            request.getExpectedRevision());
                    var rs = s.executeQuery()) {
                if (rs.next()) return Json.read(rs.getString(1), BookedTrip.class);
            }
            BookedTrip old = trip(c, request.getTripId(), true);
            int expected = request.getExpectedRevision();
            if (expected < 0) return unavailable(request, old);
            if (old != null && old.getRevision() == expected + 1 && Arrays.equals(keys(old), request.getFlightKeys()))
                return old;
            if ((old == null ? 0 : old.getRevision()) != expected) return unavailable(request, old);
            if (old != null && !old.getPassengerId().equals(request.getPassengerId()))
                throw new BadRequestException("Passenger does not own trip");
            Set<String> all = new TreeSet<>(List.of(request.getFlightKeys()));
            if (old != null) all.addAll(List.of(keys(old)));
            Map<String, FlightLeg> legs = new HashMap<>();
            Map<String, Integer> seats = new HashMap<>();
            Map<String, FlightStatus> statuses = new HashMap<>();
            for (String key : all)
                try (var s = sql(c, "SELECT * FROM flights WHERE flight_key=? FOR UPDATE", key);
                        var rs = s.executeQuery()) {
                    if (!rs.next()) throw new BadRequestException("Unknown flight");
                    legs.put(key, leg(rs));
                    seats.put(key, rs.getInt("seats"));
                    statuses.put(key, new FlightStatus(leg(rs), rs.getInt("delay_minutes"), rs.getLong("sequence")));
                }
            Set<String> previous = old == null ? Set.of() : new HashSet<>(List.of(keys(old)));
            for (String key : request.getFlightKeys())
                if (!previous.contains(key) && seats.get(key) < 1) return unavailable(request, old);
            FlightLeg[] selected =
                    Arrays.stream(request.getFlightKeys()).map(legs::get).toArray(FlightLeg[]::new);
            if (expected > 0
                    && AirlineTopology.assess(
                                    new BookedTrip(
                                            request.getTripId(),
                                            request.getPassengerId(),
                                            expected,
                                            "CONFIRMED",
                                            selected),
                                    statuses)
                            .status()
                            .equals("AT_RISK")) return unavailable(request, old);
            for (int i = 1; i < selected.length; i++)
                if (!selected[i - 1].getDestination().equals(selected[i].getOrigin())
                        || !Instant.parse(selected[i - 1].getArrival())
                                .plusSeconds(35 * 60)
                                .isBefore(Instant.parse(selected[i].getBoardingCloses())
                                        .plusSeconds(1))) throw new BadRequestException("Invalid connecting itinerary");
            Set<String> next = new HashSet<>(List.of(request.getFlightKeys()));
            for (String key : all) {
                int delta = (previous.contains(key) ? 1 : 0) - (next.contains(key) ? 1 : 0);
                if (delta != 0)
                    try (var s = sql(c, "UPDATE flights SET seats=seats+? WHERE flight_key=?", delta, key)) {
                        s.executeUpdate();
                    }
            }
            BookedTrip booked =
                    new BookedTrip(request.getTripId(), request.getPassengerId(), expected + 1, "CONFIRMED", selected);
            try (var s = sql(
                    c,
                    "INSERT INTO trips VALUES(?,?,?,?,?::jsonb) ON CONFLICT(trip_id) DO UPDATE SET revision=EXCLUDED.revision,status=EXCLUDED.status,data=EXCLUDED.data",
                    booked.getTripId(),
                    booked.getPassengerId(),
                    booked.getRevision(),
                    booked.getStatus(),
                    Json.write(booked))) {
                s.executeUpdate();
            }
            try (var s = sql(
                    c,
                    "INSERT INTO reservation_results VALUES(?,?,?::jsonb)",
                    request.getTripId(),
                    expected,
                    Json.write(booked))) {
                s.executeUpdate();
            }
            if (!request.getProposalId().isEmpty()) finish(c, request.getProposalId(), "REBOOKED");
            return booked;
        });
    }

    static String[] keys(BookedTrip trip) {
        return Arrays.stream(trip.getLegs()).map(FlightLeg::getFlightKey).toArray(String[]::new);
    }

    static BookedTrip unavailable(ReservationRequest r, BookedTrip old) {
        return new BookedTrip(
                r.getTripId(),
                r.getPassengerId(),
                old == null ? 0 : old.getRevision(),
                "UNAVAILABLE",
                old == null ? new FlightLeg[0] : old.getLegs());
    }

    public Map<String, Integer> inventory() {
        return transaction(c -> {
            Map<String, Integer> m = new HashMap<>();
            try (var s = sql(c, "SELECT flight_key,seats FROM flights");
                    var rs = s.executeQuery()) {
                while (rs.next()) m.put(rs.getString(1), rs.getInt(2));
            }
            return m;
        });
    }

    public Proposal saveProposal(Proposal proposal) {
        return transaction(c -> {
            try (var s = sql(
                    c,
                    "INSERT INTO proposals VALUES(?,?,?, ?,?::timestamptz,?::jsonb,NULL) ON CONFLICT(trip_id,revision) DO NOTHING",
                    proposal.getId(),
                    proposal.getTripId(),
                    proposal.getRevision(),
                    proposal.getStatus(),
                    proposal.getDeadline(),
                    Json.write(proposal))) {
                s.executeUpdate();
            }
            return proposal(c, proposal.getId(), false);
        });
    }

    static Proposal proposal(Connection c, String id, boolean lock) throws SQLException {
        try (var s = sql(c, "SELECT data,status FROM proposals WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
                var rs = s.executeQuery()) {
            if (!rs.next()) throw new NotFoundException("Proposal not found");
            Proposal p = Json.read(rs.getString(1), Proposal.class);
            p.setStatus(rs.getString(2));
            return p;
        }
    }

    public Proposal proposal(String id) {
        return transaction(c -> proposal(c, id, false));
    }

    public List<Proposal> proposals() {
        return transaction(c -> {
            List<Proposal> out = new ArrayList<>();
            try (var s = sql(c, "SELECT data,status FROM proposals ORDER BY deadline DESC LIMIT 300");
                    var rs = s.executeQuery()) {
                while (rs.next()) {
                    Proposal p = Json.read(rs.getString(1), Proposal.class);
                    p.setStatus(rs.getString(2));
                    out.add(p);
                }
            }
            return out;
        });
    }
    /** Persist intent before delivery. An identical HTTP retry re-delivers the same correlated event. */
    public PassengerDecision decision(String id, boolean accepted, String optionId) {
        return transaction(c -> {
            Proposal p = proposal(c, id, true);
            PassengerDecision decision = new PassengerDecision(id, p.getTripId(), accepted, accepted ? optionId : "");
            try (var s = sql(c, "SELECT decision FROM proposals WHERE id=?", id);
                    var rs = s.executeQuery()) {
                rs.next();
                String prior = rs.getString(1);
                if (prior != null) {
                    if (!prior.equals(Json.write(decision))
                            && !Json.write(Json.read(prior, PassengerDecision.class))
                                    .equals(Json.write(decision)))
                        throw new ClientErrorException("A different response was already received", 409);
                    return Json.read(prior, PassengerDecision.class);
                }
            }
            if (!p.getStatus().equals("PENDING") || !Instant.now().isBefore(Instant.parse(p.getDeadline())))
                throw new ClientErrorException("This offer is closed", 409);
            if (accepted
                    && Arrays.stream(p.getOptions())
                            .noneMatch(o -> o.getOptionId().equals(optionId)))
                throw new BadRequestException("Choose an offered itinerary");
            try (var s = sql(c, "UPDATE proposals SET decision=?::jsonb WHERE id=?", Json.write(decision), id)) {
                s.executeUpdate();
            }
            return decision;
        });
    }

    public RebookingOutcome finish(String id, String status) {
        return transaction(c -> {
            Proposal p = proposal(c, id, true);
            finish(c, id, status);
            return new RebookingOutcome(
                    id, p.getTripId(), proposal(c, id, false).getStatus());
        });
    }

    static void finish(Connection c, String id, String status) throws SQLException {
        try (var s =
                sql(c, "UPDATE proposals SET status=? WHERE id=? AND status IN ('PENDING','ACCEPTED')", status, id)) {
            s.executeUpdate();
        }
    }

    public BookedTrip cancel(String id) {
        return transaction(c -> {
            try (var s = sql(c, "SELECT pg_advisory_xact_lock(hashtext(?))", id)) {
                s.execute();
            }
            BookedTrip old = trip(c, id, true);
            if (old == null) throw new NotFoundException("Trip not found");
            if (old.getStatus().equals("CANCELLED")) return old;
            for (String key : new TreeSet<>(List.of(keys(old))))
                try (var s = sql(c, "UPDATE flights SET seats=seats+1 WHERE flight_key=?", key)) {
                    s.executeUpdate();
                }
            BookedTrip cancelled =
                    new BookedTrip(id, old.getPassengerId(), old.getRevision() + 1, "CANCELLED", old.getLegs());
            try (var s = sql(
                    c,
                    "UPDATE trips SET revision=?,status='CANCELLED',data=?::jsonb WHERE trip_id=?",
                    cancelled.getRevision(),
                    Json.write(cancelled),
                    id)) {
                s.executeUpdate();
            }
            return cancelled;
        });
    }
}
