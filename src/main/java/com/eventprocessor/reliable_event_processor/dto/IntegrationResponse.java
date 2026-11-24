package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record IntegrationResponse(
        Long id,
        String tenantId,
        String name,
        String integrationType,
        String baseUrl,
        String status,
        String supportedOperationTypes,
        String authType,
        String authHeaderName,
        String secretPrefix,
        Integer timeoutMs,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastConnectionAt,
        String lastConnectionStatus,
        String lastConnectionError
) {}
