package com.eventprocessor.reliable_event_processor.dto;

import com.eventprocessor.reliable_event_processor.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}

