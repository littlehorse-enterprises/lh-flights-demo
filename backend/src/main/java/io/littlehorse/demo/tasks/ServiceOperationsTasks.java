package io.littlehorse.demo.tasks;

import io.littlehorse.demo.store.DemoStore;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import jakarta.inject.Inject;

@LHTask
public class ServiceOperationsTasks {
    private final DemoStore store;

    @Inject
    public ServiceOperationsTasks(DemoStore store) {
        this.store = store;
    }

    @LHTaskMethod("consult-local-rules")
    public String consultLocalRules(String customerId, String municipalityCode, String scenario) {
        return switch (scenario) {
            case "warning" -> "WARN";
            case "tag-upsell" -> "OFFER_TAGS";
            case "assess-fee" -> "ASSESS_FEE";
            default -> municipalityCode.endsWith("3") ? "OFFER_TAGS" : "WARN";
        };
    }

    @LHTaskMethod("record-exception")
    public String recordException(String eventId, String customerId, String truckId, String exceptionType) {
        store.exception(eventId, customerId, truckId, exceptionType);
        return eventId;
    }

    @LHTaskMethod("create-dispatch")
    public String createDispatch(String serviceStopId, String customerId, String address, String municipalityCode) {
        return recordDispatch(serviceStopId, customerId, address, municipalityCode, "scheduled");
    }

    @LHTaskMethod("create-dispatch-next-day")
    public String createNextDayDispatch(
            String serviceStopId, String customerId, String address, String municipalityCode, String dispatchDate) {
        return recordDispatch(serviceStopId, customerId, address, municipalityCode, dispatchDate);
    }

    private String recordDispatch(
            String serviceStopId, String customerId, String address, String municipalityCode, String dispatchDate) {
        store.activity(
                "dispatch",
                "Corrective pickup dispatched",
                "%s in %s on %s".formatted(address, municipalityCode, dispatchDate),
                customerId,
                null,
                "waiting");
        return serviceStopId;
    }

    @LHTaskMethod("notify-service-status")
    public String notifyStatus(String eventId, String customerId, String status) {
        store.activity("notification", "Customer status updated", status, customerId, null, "sent");
        return status;
    }

    @LHTaskMethod("record-service-recovery")
    public String recordRecovery(String eventId, String customerId, String truckId) {
        store.recovered(eventId, customerId, truckId);
        return eventId;
    }
}
