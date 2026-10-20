package com.eventprocessor.reliable_event_processor.dto;

import java.time.Instant;

public record EventResponse(
    String idempotencyKey,
    String eventType,
    String resourceId,
    Integer deductedQuantity,
    Integer remainingQuantity,
    String status,
    boolean idempotentReplay,
    Instant processedAt
) {
}
