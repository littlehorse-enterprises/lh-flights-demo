package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("rebooking-proposal")
public class Proposal {
    private String id;
    private String tripId;
    private String passengerId;
    private int revision;
    private String status;
    private String deadline;
    private String workflowId;
    private String explanation;
    private String source;
    private String recommendedOptionId;
    private Itinerary[] options;

    public Proposal() {}

    public Proposal(
            String id,
            String tripId,
            String passengerId,
            int revision,
            String status,
            String deadline,
            String workflowId,
            String explanation,
            String source,
            String recommendedOptionId,
            Itinerary[] options) {
        this.id = id;
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.revision = revision;
        this.status = status;
        this.deadline = deadline;
        this.workflowId = workflowId;
        this.explanation = explanation;
        this.source = source;
        this.recommendedOptionId = recommendedOptionId;
        this.options = options;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRecommendedOptionId() {
        return recommendedOptionId;
    }

    public void setRecommendedOptionId(String recommendedOptionId) {
        this.recommendedOptionId = recommendedOptionId;
    }

    public Itinerary[] getOptions() {
        return options;
    }

    public void setOptions(Itinerary[] options) {
        this.options = options;
    }
}
