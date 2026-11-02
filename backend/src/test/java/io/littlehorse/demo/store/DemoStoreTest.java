package io.littlehorse.demo.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DemoStoreTest {
    @Test
    void looksUpScriptedCustomerContactPreferences() {
        DemoStore store = new DemoStore();

        assertEquals("PUSH", store.lookupContactMethod("customer-demo-001"));
        assertEquals("TEXT", store.lookupContactMethod("customer-demo-002"));
        assertEquals("PHONE", store.lookupContactMethod("customer-demo-003"));
    }

    @Test
    void countsIndependentActionsWithTheSameBusinessEventId() {
        DemoStore store = new DemoStore();

        store.exception("event-1", "customer-1", "truck-1", "Exception");
        store.ledger("event-1", "customer-1", "Resolved");
        store.recovered("event-1", "customer-1", "truck-1");

        assertEquals(1, store.outcomes().exceptionsDetected());
        assertEquals(1, store.outcomes().ledgerEntries());
        assertEquals(1, store.outcomes().failedServicesRecovered());
    }

    @Test
    void deduplicatesRetriesWithinAnAction() {
        DemoStore store = new DemoStore();

        store.purchase("payment-1", "customer-1", 15, "corrective-pickup");
        store.purchase("payment-1", "customer-1", 15, "corrective-pickup");

        assertEquals(1, store.outcomes().correctivePickupsSold());
        assertEquals(15, store.outcomes().incrementalRevenue());
    }
}
