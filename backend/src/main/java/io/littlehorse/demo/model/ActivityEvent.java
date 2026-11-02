package io.littlehorse.demo.model;

import java.time.Instant;

public record ActivityEvent(
        String id,
        Instant time,
        String type,
        String title,
        String detail,
        String customerId,
        String truckId,
        String status) {}
