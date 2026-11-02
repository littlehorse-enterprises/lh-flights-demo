package io.littlehorse.demo.tasks;

import io.littlehorse.demo.store.DemoStore;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import jakarta.inject.Inject;

@LHTask
public class BillingTasks {
    private final DemoStore store;

    @Inject
    public BillingTasks(DemoStore store) {
        this.store = store;
    }

    @LHTaskMethod("capture-purchase")
    public double capturePurchase(String eventId, String customerId, double amount, String kind) {
        store.purchase(eventId, customerId, amount, kind);
        return amount;
    }
}
