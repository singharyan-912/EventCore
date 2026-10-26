package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record EventDetailResponse(
        String idempotencyKey,
        String eventType,
        String resourceId,
        String status,
        Integer responseStatusCode,
        Integer retryCount,
        Integer maxRetries,
        String lastFailureReason,
        OffsetDateTime nextRetryAt,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String requestPayload,
        String responsePayload,
        OutboxDetailResponse outbox
) {}
