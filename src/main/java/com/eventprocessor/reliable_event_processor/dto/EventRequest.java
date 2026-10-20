package com.eventprocessor.reliable_event_processor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EventRequest(
    @NotBlank(message = "idempotencyKey must not be blank")
    String idempotencyKey,

    @NotBlank(message = "eventType must not be blank")
    @Pattern(regexp = "^INVENTORY_DEDUCT$", message = "eventType must be INVENTORY_DEDUCT")
    String eventType,

    @NotBlank(message = "resourceId must not be blank")
    String resourceId,

    @NotNull(message = "quantity must not be null")
    @Min(value = 1, message = "quantity must be a positive integer")
    Integer quantity
) {
}
