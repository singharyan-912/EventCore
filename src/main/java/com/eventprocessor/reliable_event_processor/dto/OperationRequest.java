package com.eventprocessor.reliable_event_processor.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OperationRequest(
        @NotBlank(message = "idempotencyKey must not be blank")
        String idempotencyKey,

        @NotBlank(message = "operationType must not be blank")
        String operationType,

        @NotBlank(message = "resourceId must not be blank")
        String resourceId,

        @NotNull(message = "payload must not be null")
        JsonNode payload,

        String tenantId
) {
    public OperationRequest(String idempotencyKey, String operationType, String resourceId, JsonNode payload) {
        this(idempotencyKey, operationType, resourceId, payload, null);
    }
}
