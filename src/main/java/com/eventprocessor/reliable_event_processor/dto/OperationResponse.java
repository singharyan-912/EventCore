package com.eventprocessor.reliable_event_processor.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;

public record OperationResponse(
        String idempotencyKey,
        String operationType,
        String resourceId,
        String tenantId,
        String status,
        boolean idempotentReplay,
        JsonNode result,
        Instant processedAt,
        Integer retryCount,
        OffsetDateTime nextRetryAt,
        String message
) {
    public OperationResponse(String idempotencyKey, String operationType, String resourceId,
                             String tenantId, String status, boolean idempotentReplay,
                             JsonNode result, Instant processedAt) {
        this(idempotencyKey, operationType, resourceId, tenantId, status, idempotentReplay,
                result, processedAt, 0, null, null);
    }
}
