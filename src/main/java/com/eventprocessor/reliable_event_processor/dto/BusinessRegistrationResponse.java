package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record BusinessRegistrationResponse(
        String tenantId,
        String businessName,
        String ownerUsername,
        String ownerEmail,
        String status,
        OffsetDateTime createdAt,
        String message
) {}
