package com.eventprocessor.reliable_event_processor.dto;

import com.eventprocessor.reliable_event_processor.entity.ApiKeyStatus;
import java.time.OffsetDateTime;

public record ApiKeyResponse(
        Long id,
        String name,
        String keyPrefix,
        ApiKeyStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt,
        String createdBy
) {}
