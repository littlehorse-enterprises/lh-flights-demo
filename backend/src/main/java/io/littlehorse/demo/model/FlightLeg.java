package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("flight-leg")
public class FlightLeg {
    private String flightKey;
    private String flightNumber;
    private String date;
    private String origin;
    private String destination;
    private String departure;
    private String arrival;
    private String boardingCloses;

    public FlightLeg() {}

    public FlightLeg(
            String flightKey,
            String flightNumber,
            String date,
            String origin,
            String destination,
            String departure,
            String arrival,
            String boardingCloses) {
        this.flightKey = flightKey;
        this.flightNumber = flightNumber;
        this.date = date;
        this.origin = origin;
        this.destination = destination;
        this.departure = departure;
        this.arrival = arrival;
        this.boardingCloses = boardingCloses;
    }

    public String getFlightKey() {
        return flightKey;
    }

    public void setFlightKey(String flightKey) {
        this.flightKey = flightKey;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getDeparture() {
        return departure;
    }

    public void setDeparture(String departure) {
        this.departure = departure;
    }

    public String getArrival() {
        return arrival;
    }

    public void setArrival(String arrival) {
        this.arrival = arrival;
    }

    public String getBoardingCloses() {
        return boardingCloses;
    }

    public void setBoardingCloses(String boardingCloses) {
        this.boardingCloses = boardingCloses;
    }
}
