package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record WebhookDeliveryResponse(
        Long id,
        String tenantId,
        Long webhookId,
        String eventId,
        String eventType,
        String idempotencyKey,
        String payload,
        String status,
        Integer httpStatus,
        Integer attemptCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime deliveredAt
) {}
