package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record DeadLetterResponse(
    String idempotencyKey,
    String eventType,
    String resourceId,
    String status,
    int retryCount,
    int maxRetries,
    String lastFailureReason,
    OffsetDateTime nextRetryAt,
    OffsetDateTime lastAttemptAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
