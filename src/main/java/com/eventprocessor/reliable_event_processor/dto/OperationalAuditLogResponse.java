package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record OperationalAuditLogResponse(
        Long id,
        String actionType,
        String targetIdentifier,
        String outcome,
        String failureReason,
        String principal,
        String details,
        OffsetDateTime timestamp
) {}
