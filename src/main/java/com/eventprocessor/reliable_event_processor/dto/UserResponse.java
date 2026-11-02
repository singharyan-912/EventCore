package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

