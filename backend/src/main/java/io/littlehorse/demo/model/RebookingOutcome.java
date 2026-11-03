package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("rebooking-outcome")
public class RebookingOutcome {
    private String proposalId;
    private String tripId;
    private String status;

    public RebookingOutcome() {}

    public RebookingOutcome(String proposalId, String tripId, String status) {
        this.proposalId = proposalId;
        this.tripId = tripId;
        this.status = status;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
