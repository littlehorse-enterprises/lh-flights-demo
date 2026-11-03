package io.littlehorse.demo.tasks;

import static org.junit.jupiter.api.Assertions.*;

import io.littlehorse.demo.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdvisorTest {
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
