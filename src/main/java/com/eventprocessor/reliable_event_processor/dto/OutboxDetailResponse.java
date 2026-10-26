package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record OutboxDetailResponse(
        Long id,
        String status,
        String topic,
        String partitionKey,
        Integer retryCount,
        Integer maxRetries,
        String lastError,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
