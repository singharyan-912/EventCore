package com.eventprocessor.reliable_event_processor.dto;

import java.time.Instant;
import java.time.OffsetDateTime;

public record EventResponse(
    String idempotencyKey,
    String eventType,
    String resourceId,
    Integer deductedQuantity,
    Integer remainingQuantity,
    String status,
    boolean idempotentReplay,
    Instant processedAt,
    Integer retryCount,
    OffsetDateTime nextRetryAt,
    String message
) {
    public EventResponse(String idempotencyKey, String eventType, String resourceId,
                         Integer deductedQuantity, Integer remainingQuantity,
                         String status, boolean idempotentReplay, Instant processedAt) {
        this(idempotencyKey, eventType, resourceId, deductedQuantity, remainingQuantity,
             status, idempotentReplay, processedAt, 0, null, null);
    }
}
