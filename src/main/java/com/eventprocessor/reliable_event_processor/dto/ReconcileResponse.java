package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record ReconcileResponse(
        String idempotencyKey,
        String tenantId,
        String status,
        OffsetDateTime reconciledAt,
        String reconciledBy,
        String reconciliationNotes,
        String message
) {}
