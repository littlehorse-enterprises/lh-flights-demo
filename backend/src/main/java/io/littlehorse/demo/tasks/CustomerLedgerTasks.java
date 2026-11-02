package io.littlehorse.demo.tasks;

import io.littlehorse.demo.store.DemoStore;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import jakarta.inject.Inject;

@LHTask
public class CustomerLedgerTasks {
    private final DemoStore store;

    @Inject
    public CustomerLedgerTasks(DemoStore store) {
        this.store = store;
    }

    @LHTaskMethod("record-customer-ledger")
    public String recordLedger(String eventId, String customerId, String detail) {
        store.ledger(eventId, customerId, detail);
        return eventId;
    }
}
