package com.eventprocessor.reliable_event_processor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReconcileRequest(
        @NotBlank(message = "Resolution is required")
        @Pattern(regexp = "SUCCESS|FAILURE", message = "Resolution must be either SUCCESS or FAILURE")
        String resolution,

        @NotBlank(message = "Reason is mandatory for audit trail")
        String reason,

        String notes
) {}
