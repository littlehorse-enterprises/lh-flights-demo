package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("booked-trip")
public class BookedTrip {
    private String tripId;
    private String passengerId;
    private int revision;
    private String status;
    private FlightLeg[] legs;

    public BookedTrip() {}

    public BookedTrip(String tripId, String passengerId, int revision, String status, FlightLeg[] legs) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.revision = revision;
        this.status = status;
        this.legs = legs;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public FlightLeg[] getLegs() {
        return legs;
    }

    public void setLegs(FlightLeg[] legs) {
        this.legs = legs;
    }
}
