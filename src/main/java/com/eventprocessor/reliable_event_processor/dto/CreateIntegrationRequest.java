package com.eventprocessor.reliable_event_processor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIntegrationRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        String integrationType,

        @NotBlank(message = "baseUrl must not be blank")
        String baseUrl,

        @NotBlank(message = "supportedOperationTypes must not be blank")
        String supportedOperationTypes,

        String authType,
        String authHeaderName,
        String secret,
        Integer timeoutMs
) {}
