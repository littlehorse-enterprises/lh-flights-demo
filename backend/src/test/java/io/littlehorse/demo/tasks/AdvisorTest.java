package io.littlehorse.demo.tasks;

import static org.junit.jupiter.api.Assertions.*;

import io.littlehorse.demo.model.*;
import io.littlehorse.demo.store.AirlineStore;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdvisorTest {
    static FlightLeg flight(String number, String origin, String destination, String departure, String arrival) {
        String date = "2026-11-05";
        String departureTime = date + "T" + departure + ":00Z";
        return new FlightLeg(
                number + "-" + date,
                number,
                date,
                origin,
                destination,
                departureTime,
                date + "T" + arrival + ":00Z",
                Instant.parse(departureTime).minusSeconds(900).toString());
    }

    @Test
    void adviceUsesCurrentDatabaseDelaysAndInventory() {
        FlightLeg inbound = flight("LH101", "RNO", "DEN", "15:15", "17:00");
        FlightLeg original = flight("LH220", "DEN", "BOS", "18:05", "21:50");
        FlightLeg alternative = flight("LH221", "DEN", "BOS", "19:30", "23:15");
        class SchedulingStore extends AirlineStore {
            int delay = 25;
            int seats = 1;

            SchedulingStore() {
                super(null);
            }

            @Override
            public BookedTrip trip(String id) {
                return new BookedTrip(id, "p001", 1, "CONFIRMED", new FlightLeg[] {inbound, original});
            }

            @Override
            public Passenger passenger(String id) {
                return new Passenger(id, "Passenger", "", false);
            }

            @Override
            public List<FlightStatus> flights() {
                return List.of(
                        new FlightStatus(inbound, delay, 1),
                        new FlightStatus(original, 0, 1),
                        new FlightStatus(alternative, 0, 1));
            }

            @Override
            public Map<String, Integer> inventory() {
                return Map.of(original.getFlightKey(), 1, alternative.getFlightKey(), seats);
            }
        }
        var store = new SchedulingStore();
        var tasks = new RebookingTasks(store, 180);
        assertEquals(
                alternative.getFlightKey(),
                tasks.advice("p001", "pnr").getOptions()[0].getOptionId());
        store.seats = 0;
        assertEquals(0, tasks.advice("p001", "pnr").getOptions().length);
        store.delay = 0;
        assertEquals(0, tasks.advice("p001", "pnr").getOptions().length);
        assertFalse(tasks.isBroken("pnr"));
    }

    FlightLeg leg(String number, String date, String departure) {
        return new FlightLeg(
                number + "-" + date,
                number,
                date,
                "DEN",
                "BOS",
                date + "T" + departure + ":00Z",
                date + "T23:15:00Z",
                date + "T18:00:00Z");
    }

    @Test
    void nextDayOptionsRequirePassengerPermission() {
        List<Itinerary> options = new ArrayList<>();
        FlightLeg tomorrow = leg("LH221", "2026-11-06", "19:30");
        RebookingTasks.addOption(options, new FlightLeg[0], List.of(tomorrow), "2026-11-05", false);
        assertTrue(options.isEmpty());
        RebookingTasks.addOption(options, new FlightLeg[0], List.of(tomorrow), "2026-11-05", true);
        assertTrue(options.getFirst().isOvernight());
    }

    @Test
    void rulesPreferSameDayAndTheNextDeparture() {
        FlightLeg late = leg("LH221", "2026-11-05", "19:30"),
                early = leg("LH310", "2026-11-05", "18:30"),
                tomorrow = leg("LH221", "2026-11-06", "09:30");
        Itinerary[] options = {
            new Itinerary(tomorrow.getFlightKey(), new FlightLeg[] {tomorrow}, true),
            new Itinerary(late.getFlightKey(), new FlightLeg[] {late}, false),
            new Itinerary(early.getFlightKey(), new FlightLeg[] {early}, false)
        };
        Recommendation selected = RebookingTasks.deterministic(new AdviceRequest("pnr", "p001", "", 1, options));
        assertEquals(early.getFlightKey(), selected.getOptionId());
        assertEquals("Rules", selected.getSource());
    }

    @Test
    void noOptionsExplicitlyRequestsAirportAssistance() {
        Recommendation r = RebookingTasks.deterministic(new AdviceRequest("pnr", "p001", "", 1, new Itinerary[0]));
        assertEquals("", r.getOptionId());
        assertTrue(r.getExplanation().contains("airport agent"));
    }
}
