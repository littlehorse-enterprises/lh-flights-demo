package io.littlehorse.demo.tasks;

import io.littlehorse.demo.kafka.*;
import io.littlehorse.demo.model.*;
import io.littlehorse.demo.store.AirlineStore;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.*;
import java.time.*;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@LHTask
public class RebookingTasks {
    final AirlineStore store;
    final AirlineStreams streams;
    final int responseSeconds;

    public RebookingTasks(
            AirlineStore store,
            AirlineStreams streams,
            @ConfigProperty(name = "airline.response-seconds") int responseSeconds) {
        this.store = store;
        this.streams = streams;
        this.responseSeconds = responseSeconds;
    }

    @LHTaskMethod("prepare-booking")
    public ReservationRequest prepare(String tripId, String passengerId, String[] flightNumbers, String date) {
        return store.initial(tripId, passengerId, flightNumbers, date);
    }

    @LHTaskMethod("reserve-itinerary")
    public BookedTrip reserve(ReservationRequest request) {
        return store.reserve(request);
    }

    @LHTaskMethod("cancel-booking")
    public BookedTrip cancel(String tripId) {
        return store.cancel(tripId);
    }

    @LHTaskMethod("create-advice")
    public AdviceRequest advice(String passengerId, String tripId) {
        BookedTrip trip = store.trip(tripId);
        if (trip == null || !trip.getPassengerId().equals(passengerId))
            throw new IllegalArgumentException("Booking not found for passenger");
        var passenger = store.passenger(passengerId);
        Map<String, FlightStatus> statuses = streams.statusMap();
        // Defer the task until the queryable view has the complete itinerary.
        for (FlightLeg leg : trip.getLegs())
            if (!statuses.containsKey(leg.getFlightKey()))
                throw new IllegalStateException("Flight statuses still restoring");
        List<Itinerary> options = new ArrayList<>();
        if (!trip.getStatus().equals("CANCELLED")
                && AirlineTopology.assess(trip, statuses).status().equals("AT_RISK")) {
            int broken = 0;
            while (broken < trip.getLegs().length - 1) {
                var in = statuses.get(trip.getLegs()[broken].getFlightKey());
                var out = statuses.get(trip.getLegs()[broken + 1].getFlightKey());
                if (in.arrival().plusSeconds(35 * 60).isAfter(out.boardingCloses())) break;
                broken++;
            }
            var inbound = statuses.get(trip.getLegs()[broken].getFlightKey());
            String destination = trip.getLegs()[trip.getLegs().length - 1].getDestination();
            String originalDate = trip.getLegs()[broken + 1].getDate();
            Instant earliest = inbound.arrival().plusSeconds(35 * 60);
            Map<String, Integer> seats = store.inventory();
            List<FlightStatus> flights = statuses.values().stream()
                    .filter(f -> seats.getOrDefault(f.flight().getFlightKey(), 0) > 0)
                    .sorted(Comparator.comparing(FlightStatus::departure))
                    .toList();
            FlightLeg[] prefix = Arrays.copyOf(trip.getLegs(), broken + 1);
            for (var first : flights) {
                if (!first.flight().getOrigin().equals(inbound.flight().getDestination())
                        || first.boardingCloses().isBefore(earliest)
                        || first.departure().isAfter(earliest.plusSeconds(36 * 3600))) continue;
                if (first.flight().getDestination().equals(destination))
                    addOption(options, prefix, List.of(first.flight()), originalDate, passenger.allowOvernight());
                else
                    for (var second : flights)
                        if (second.flight().getOrigin().equals(first.flight().getDestination())
                                && second.flight().getDestination().equals(destination)
                                && !second.boardingCloses()
                                        .isBefore(first.arrival().plusSeconds(40 * 60))
                                && second.departure().isBefore(first.arrival().plusSeconds(6 * 3600)))
                            addOption(
                                    options,
                                    prefix,
                                    List.of(first.flight(), second.flight()),
                                    originalDate,
                                    passenger.allowOvernight());
            }
        }
        return new AdviceRequest(
                tripId,
                passengerId,
                passenger.preferences(),
                trip.getRevision(),
                options.stream().limit(8).toArray(Itinerary[]::new));
    }

    static void addOption(
            List<Itinerary> out,
            FlightLeg[] prefix,
            List<FlightLeg> replacement,
            String originalDate,
            boolean allowOvernight) {
        boolean overnight = replacement.stream().anyMatch(leg -> !leg.getDate().equals(originalDate));
        if (overnight && !allowOvernight) return;
        List<FlightLeg> all = new ArrayList<>(List.of(prefix));
        all.addAll(replacement);
        out.add(new Itinerary(
                String.join(
                        "_", replacement.stream().map(FlightLeg::getFlightKey).toList()),
                all.toArray(FlightLeg[]::new),
                overnight));
    }

    public static Recommendation deterministic(AdviceRequest request) {
        if (request.getOptions().length == 0)
            return new Recommendation(
                    "", "No suitable itinerary is available. Please contact an airport agent.", "Rules");
        Itinerary selected = Arrays.stream(request.getOptions())
                .min(Comparator.comparing(Itinerary::isOvernight).thenComparing(o -> Arrays.stream(o.getLegs())
                        .filter(l -> l.getFlightKey().equals(o.getOptionId().split("_")[0]))
                        .findFirst()
                        .orElseThrow()
                        .getDeparture()))
                .orElseThrow();
        return new Recommendation(
                selected.getOptionId(),
                selected.isOvernight()
                        ? "The next available itinerary departs tomorrow. An airport agent can help with overnight arrangements."
                        : "This is the next available itinerary with enough transfer time and seats.",
                "Rules");
    }

    @LHTaskMethod("fallback-advice")
    public Recommendation fallback(AdviceRequest request) {
        return deterministic(request);
    }

    @LHTaskMethod("create-proposal")
    public Proposal propose(AdviceRequest request, Recommendation recommendation, WorkerContext context) {
        Recommendation valid = recommendation;
        if (Arrays.stream(request.getOptions()).noneMatch(o -> o.getOptionId().equals(recommendation.getOptionId())))
            valid = deterministic(request);
        // Recheck all deterministic constraints after the model; never trust model-invented flights.
        AdviceRequest current = advice(request.getPassengerId(), request.getTripId());
        Set<String> allowed = new HashSet<>();
        for (var o : current.getOptions()) allowed.add(o.getOptionId());
        Itinerary[] options = Arrays.stream(request.getOptions())
                .filter(o -> allowed.contains(o.getOptionId()))
                .toArray(Itinerary[]::new);
        String status =
                options.length == 0 ? (isBroken(request.getTripId()) ? "MANUAL_ASSISTANCE" : "RESOLVED") : "PENDING";
        String chosen = valid.getOptionId();
        if (Arrays.stream(options).noneMatch(o -> o.getOptionId().equals(chosen)))
            valid = deterministic(new AdviceRequest(
                    request.getTripId(),
                    request.getPassengerId(),
                    request.getPreferences(),
                    request.getRevision(),
                    options));
        String wfId = context.getWfRunId().getId();
        Proposal p = new Proposal(
                "offer-" + request.getTripId() + "-v" + request.getRevision(),
                request.getTripId(),
                request.getPassengerId(),
                request.getRevision(),
                status,
                Instant.now().plusSeconds(responseSeconds).toString(),
                wfId,
                valid.getExplanation(),
                valid.getSource(),
                valid.getOptionId(),
                options);
        return store.saveProposal(p);
    }

    boolean isBroken(String id) {
        BookedTrip trip = store.trip(id);
        return !trip.getStatus().equals("CANCELLED")
                && AirlineTopology.assess(trip, streams.statusMap()).status().equals("AT_RISK");
    }

    @LHTaskMethod("prepare-change")
    public ReservationRequest change(Proposal proposal, PassengerDecision decision) {
        if (!proposal.getId().equals(decision.getProposalId())
                || !proposal.getTripId().equals(decision.getTripId()))
            throw new IllegalArgumentException("Decision does not match offer");
        Itinerary choice = Arrays.stream(proposal.getOptions())
                .filter(o -> o.getOptionId().equals(decision.getOptionId()))
                .findFirst()
                .orElseThrow();
        // Re-evaluate latest operational statuses before attempting the atomic inventory change.
        AdviceRequest current = advice(proposal.getPassengerId(), proposal.getTripId());
        if (Arrays.stream(current.getOptions()).noneMatch(o -> o.getOptionId().equals(choice.getOptionId())))
            return new ReservationRequest(
                    proposal.getTripId(), proposal.getPassengerId(), -1, new String[0], proposal.getId());
        return new ReservationRequest(
                proposal.getTripId(),
                proposal.getPassengerId(),
                proposal.getRevision(),
                Arrays.stream(choice.getLegs()).map(FlightLeg::getFlightKey).toArray(String[]::new),
                proposal.getId());
    }

    @LHTaskMethod("finish-rebooking")
    public RebookingOutcome finish(String proposalId, String status) {
        return store.finish(proposalId, status);
    }
}
