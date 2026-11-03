package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("reservation-request")
public class ReservationRequest {
    private String tripId;
    private String passengerId;
    private int expectedRevision;
    private String[] flightKeys;
    private String proposalId;

    public ReservationRequest() {}

    public ReservationRequest(
            String tripId, String passengerId, int expectedRevision, String[] flightKeys, String proposalId) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.expectedRevision = expectedRevision;
        this.flightKeys = flightKeys;
        this.proposalId = proposalId;
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

    public int getExpectedRevision() {
        return expectedRevision;
    }

    public void setExpectedRevision(int expectedRevision) {
        this.expectedRevision = expectedRevision;
    }

    public String[] getFlightKeys() {
        return flightKeys;
    }

    public void setFlightKeys(String[] flightKeys) {
        this.flightKeys = flightKeys;
    }

    public String getProposalId() {
        return proposalId;
    }

    public void setProposalId(String proposalId) {
        this.proposalId = proposalId;
    }
}
