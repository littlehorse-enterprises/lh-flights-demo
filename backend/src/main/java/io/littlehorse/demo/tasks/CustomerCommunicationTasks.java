package io.littlehorse.demo.tasks;

import io.littlehorse.demo.store.DemoStore;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import jakarta.inject.Inject;

@LHTask
public class CustomerCommunicationTasks {
    private final DemoStore store;

    @Inject
    public CustomerCommunicationTasks(DemoStore store) {
        this.store = store;
    }

    @LHTaskMethod("lookup-contact-method")
    public String lookupContactMethod(String customerId) {
        return store.lookupContactMethod(customerId);
    }

    @LHTaskMethod("contact-center-call")
    public boolean contactCenterCall(String eventId, String customerId, String message, boolean accepted) {
        store.interaction(eventId, customerId, "PHONE", message);
        return accepted;
    }

    @LHTaskMethod("send-text-message")
    public boolean sendTextMessage(String eventId, String customerId, String message, boolean accepted) {
        store.interaction(eventId, customerId, "TEXT", message);
        return accepted;
    }

    @LHTaskMethod("send-push-notification")
    public boolean sendPushNotification(String eventId, String customerId, String message, boolean accepted) {
        store.interaction(eventId, customerId, "PUSH", message);
        return accepted;
    }

    @LHTaskMethod("record-customer-response")
    public boolean recordCustomerResponse(String eventId, String customerId, String channel, boolean accepted) {
        store.customerResponse(eventId, customerId, channel, accepted);
        return accepted;
    }
}
