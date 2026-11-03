package io.littlehorse.demo.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record FlightStatus(FlightLeg flight, int delayMinutes, long sequence) {
    public Instant arrival() {
        return Instant.parse(flight.getArrival()).plus(delayMinutes, ChronoUnit.MINUTES);
    }

    public Instant departure() {
        return Instant.parse(flight.getDeparture()).plus(delayMinutes, ChronoUnit.MINUTES);
    }

    public Instant boardingCloses() {
        return Instant.parse(flight.getBoardingCloses()).plus(delayMinutes, ChronoUnit.MINUTES);
    }
}
