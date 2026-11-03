package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("passenger-decision")
public class PassengerDecision {
    private String proposalId;
    private String tripId;
    private boolean accepted;
    private String optionId;

    public PassengerDecision() {}

    public PassengerDecision(String proposalId, String tripId, boolean accepted, String optionId) {
        this.proposalId = proposalId;
        this.tripId = tripId;
        this.accepted = accepted;
        this.optionId = optionId;
    }

    public String getProposalId() {
        return proposalId;
    }

    public void setProposalId(String proposalId) {
        this.proposalId = proposalId;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }
}
