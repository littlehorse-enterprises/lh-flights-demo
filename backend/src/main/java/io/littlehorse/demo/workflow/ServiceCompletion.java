package io.littlehorse.demo.workflow;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("service-completion")
public class ServiceCompletion {
    private String eventId;
    private String customerId;
    private String truckId;
    private String serviceStopId;
    private String status;
    private String completionSource;
    private boolean recovered;
    private String completedAt;

    public ServiceCompletion() {}

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getTruckId() {
        return truckId;
    }

    public void setTruckId(String truckId) {
        this.truckId = truckId;
    }

    public String getServiceStopId() {
        return serviceStopId;
    }

    public void setServiceStopId(String serviceStopId) {
        this.serviceStopId = serviceStopId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCompletionSource() {
        return completionSource;
    }

    public void setCompletionSource(String completionSource) {
        this.completionSource = completionSource;
    }

    public boolean isRecovered() {
        return recovered;
    }

    public void setRecovered(boolean recovered) {
        this.recovered = recovered;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }
}
