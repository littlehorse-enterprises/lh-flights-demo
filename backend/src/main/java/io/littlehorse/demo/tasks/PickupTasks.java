package io.littlehorse.demo.tasks;

import io.littlehorse.demo.store.DemoStore;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import jakarta.inject.Inject;

@LHTask
public class PickupTasks {
    private final DemoStore store;

    @Inject
    public PickupTasks(DemoStore store) {
        this.store = store;
    }

    @LHTaskMethod("record-pickup-ledger")
    public String recordPickup(
            String eventId, String customerId, String truckId, String serviceStopId, String municipalityCode) {
        store.pickup(eventId, customerId, truckId);
        return serviceStopId;
    }
}
