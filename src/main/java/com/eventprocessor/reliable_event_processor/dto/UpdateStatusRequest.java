package com.eventprocessor.reliable_event_processor.dto;

import com.eventprocessor.reliable_event_processor.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "Status is required")
        UserStatus status
) {
}

