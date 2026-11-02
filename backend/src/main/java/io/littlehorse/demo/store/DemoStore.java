package io.littlehorse.demo.store;

import io.littlehorse.demo.model.ActivityEvent;
import io.littlehorse.demo.model.OutcomeSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class DemoStore {
    private static final Map<String, String> CONTACT_PREFERENCES = Map.of(
            "customer-demo-001", "PUSH",
            "customer-demo-002", "TEXT",
            "customer-demo-003", "PHONE",
            "customer-demo-004", "TEXT",
            "customer-demo-005", "PUSH");
    private final Set<String> trucks = ConcurrentHashMap.newKeySet();
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedDeque<ActivityEvent> activity = new ConcurrentLinkedDeque<>();
    private final AtomicLong serviceEvents = new AtomicLong();
    private final AtomicLong exceptions = new AtomicLong();
    private final AtomicLong noPhoneCalls = new AtomicLong();
    private final AtomicLong correctivePickups = new AtomicLong();
    private final AtomicLong tagPurchases = new AtomicLong();
    private final AtomicLong revenueCents = new AtomicLong();
    private final AtomicLong recoveredServices = new AtomicLong();
    private final AtomicLong ledgerEntries = new AtomicLong();
    private final AtomicInteger activeSimulation = new AtomicInteger();
    private volatile Instant simulationStartedAt;

    public void location(String eventId, String truckId) {
        trucks.add(truckId);
        once("location:" + eventId, () -> serviceEvents.incrementAndGet());
    }

    public void pickup(String eventId, String customerId, String truckId) {
        trucks.add(truckId);
        once("pickup:" + eventId, () -> {
            serviceEvents.incrementAndGet();
            ledgerEntries.incrementAndGet();
        });
    }

    public void exception(String eventId, String customerId, String truckId, String title) {
        once("exception:" + eventId, () -> {
            exceptions.incrementAndGet();
            activity("exception", title, "Exception workflow started", customerId, truckId, "open");
        });
    }

    public String lookupContactMethod(String customerId) {
        return CONTACT_PREFERENCES.getOrDefault(
                customerId,
                Math.floorMod(customerId.hashCode(), 3) == 0
                        ? "PHONE"
                        : Math.floorMod(customerId.hashCode(), 3) == 1 ? "TEXT" : "PUSH");
    }

    public void interaction(String eventId, String customerId, String method, String detail) {
        once("interaction:" + eventId, () -> {
            if (!"PHONE".equals(method)) noPhoneCalls.incrementAndGet();
            activity(
                    "customer",
                    "Customer contacted",
                    "%s via %s: %s".formatted(method, customerId, detail),
                    customerId,
                    null,
                    "resolved");
        });
    }

    public void customerResponse(String eventId, String customerId, String channel, boolean accepted) {
        once(
                "response:" + eventId,
                () -> activity(
                        "customer-response",
                        "Customer response received",
                        "%s via %s".formatted(accepted ? "Accepted" : "Declined", channel),
                        customerId,
                        null,
                        accepted ? "accepted" : "declined"));
    }

    public void purchase(String eventId, String customerId, double amount, String kind) {
        once("purchase:" + eventId, () -> {
            if ("tags".equals(kind)) tagPurchases.incrementAndGet();
            if ("corrective-pickup".equals(kind)) correctivePickups.incrementAndGet();
            revenueCents.addAndGet(BigDecimal.valueOf(amount).movePointRight(2).longValue());
            activity(
                    "revenue",
                    "Revenue captured",
                    "$%.2f for %s".formatted(amount, kind),
                    customerId,
                    null,
                    "resolved");
        });
    }

    public void ledger(String eventId, String customerId, String detail) {
        once("ledger:" + eventId, () -> {
            ledgerEntries.incrementAndGet();
            activity("ledger", "Customer history updated", detail, customerId, null, "recorded");
        });
    }

    public void recovered(String eventId, String customerId, String truckId) {
        once("recovery:" + eventId, () -> {
            recoveredServices.incrementAndGet();
            activity(
                    "recovery",
                    "Failed service recovered",
                    "Replacement pickup verified",
                    customerId,
                    truckId,
                    "resolved");
        });
    }

    public void activity(String type, String title, String detail, String customerId, String truckId, String status) {
        activity.addFirst(new ActivityEvent(
                UUID.randomUUID().toString(), Instant.now(), type, title, detail, customerId, truckId, status));
        while (activity.size() > 80) activity.pollLast();
    }

    public List<ActivityEvent> activity() {
        ArrayList<ActivityEvent> events = new ArrayList<>(activity);
        events.sort(Comparator.comparing(ActivityEvent::time).reversed());
        return events;
    }

    public void simulationStarted() {
        if (activeSimulation.getAndIncrement() == 0) simulationStartedAt = Instant.now();
    }

    public void simulationStopped() {
        activeSimulation.updateAndGet(value -> Math.max(0, value - 1));
    }

    public OutcomeSnapshot outcomes() {
        long elapsed = simulationStartedAt == null
                ? 0
                : Duration.between(simulationStartedAt, Instant.now()).toSeconds();
        return new OutcomeSnapshot(
                trucks.size(),
                serviceEvents.get(),
                exceptions.get(),
                noPhoneCalls.get(),
                correctivePickups.get(),
                tagPurchases.get(),
                revenueCents.get() / 100.0,
                recoveredServices.get(),
                ledgerEntries.get(),
                activeSimulation.get() > 0,
                elapsed);
    }

    public void reset() {
        trucks.clear();
        processedEvents.clear();
        activity.clear();
        serviceEvents.set(0);
        exceptions.set(0);
        noPhoneCalls.set(0);
        correctivePickups.set(0);
        tagPurchases.set(0);
        revenueCents.set(0);
        recoveredServices.set(0);
        ledgerEntries.set(0);
        activeSimulation.set(0);
        simulationStartedAt = null;
    }

    private void once(String id, Runnable action) {
        if (processedEvents.add(id)) action.run();
    }
}
