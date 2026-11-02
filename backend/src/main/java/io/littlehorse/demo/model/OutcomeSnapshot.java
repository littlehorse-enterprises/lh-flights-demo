package io.littlehorse.demo.model;

public record OutcomeSnapshot(
        int trucksSimulated,
        long serviceEventsProcessed,
        long exceptionsDetected,
        long interactionsWithoutPhoneCall,
        long correctivePickupsSold,
        long additionalTrashPurchases,
        double incrementalRevenue,
        long failedServicesRecovered,
        long ledgerEntries,
        boolean simulationRunning,
        long elapsedSeconds) {}
