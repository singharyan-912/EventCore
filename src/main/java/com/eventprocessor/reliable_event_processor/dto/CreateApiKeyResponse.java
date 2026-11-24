package com.eventprocessor.reliable_event_processor.dto;

import com.eventprocessor.reliable_event_processor.entity.ApiKeyStatus;
import java.time.OffsetDateTime;

public record CreateApiKeyResponse(
        Long id,
        String name,
        String keyPrefix,
        String secretKey,
        ApiKeyStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        String message
) {}
