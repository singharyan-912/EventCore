package com.eventprocessor.reliable_event_processor.dto;

import java.time.Instant;

/**
 * Standard envelope contract for events published to Kafka via the Transactional Outbox.
 */
public record KafkaEventMessage(
    String eventId,
    String eventType,
    String resourceId,
    String idempotencyKey,
    String payload,
    Instant timestamp
) {}
