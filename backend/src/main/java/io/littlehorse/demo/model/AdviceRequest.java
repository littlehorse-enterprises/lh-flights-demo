package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("advice-request")
public class AdviceRequest {
    private String tripId;
    private String passengerId;
    private String preferences;
    private int revision;
    private Itinerary[] options;

    public AdviceRequest() {}

    public AdviceRequest(String tripId, String passengerId, String preferences, int revision, Itinerary[] options) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.preferences = preferences;
        this.revision = revision;
        this.options = options;
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

    public String getPreferences() {
        return preferences;
    }

    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Itinerary[] getOptions() {
        return options;
    }

    public void setOptions(Itinerary[] options) {
        this.options = options;
    }
}
