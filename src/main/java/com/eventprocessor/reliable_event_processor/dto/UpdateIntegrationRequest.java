package com.eventprocessor.reliable_event_processor.dto;

public record UpdateIntegrationRequest(
        String name,
        String baseUrl,
        String supportedOperationTypes,
        String authType,
        String authHeaderName,
        String secret,
        Integer timeoutMs,
        String status
) {}
