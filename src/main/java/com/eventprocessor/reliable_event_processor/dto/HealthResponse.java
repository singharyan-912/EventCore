package com.eventprocessor.reliable_event_processor.dto;

public record HealthResponse(
        String status,
        String message
) {
}
