package com.eventprocessor.reliable_event_processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record ConnectionTestResponse(
        Long integrationId,
        @JsonProperty("successful") boolean successful,
        String message,
        OffsetDateTime testedAt
) {
    public boolean success() {
        return successful;
    }
}
