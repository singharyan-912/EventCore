package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record ReconciliationDetailResponse(
        String idempotencyKey,
        String tenantId,
        String eventType,
        String resourceId,
        String status,
        String lastFailureReason,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime reconciledAt,
        String reconciledBy,
        String reconciliationNotes,
        String requestPayload
) {}
