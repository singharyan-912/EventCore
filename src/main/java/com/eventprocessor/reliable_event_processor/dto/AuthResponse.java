package com.eventprocessor.reliable_event_processor.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String username,
        String role
) {
    public AuthResponse(String token, long expiresInMs, String username, String role) {
        this(token, "Bearer", expiresInMs, username, role);
    }
}

