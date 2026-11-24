package com.eventprocessor.reliable_event_processor.dto;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        String tenantId
) {
    public UserProfileResponse(Long id, String username, String email, String role, String status) {
        this(id, username, email, role, status, "DEFAULT_BUSINESS");
    }
}

