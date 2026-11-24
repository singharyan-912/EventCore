package com.eventprocessor.reliable_event_processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record WebhookResponse(
        Long id,
        String tenantId,
        String name,
        String destinationUrl,
        String status,
        String subscribedEvents,
        String secretPrefix,
        @JsonProperty("signingSecret") String signingSecret, // Populated ONLY on create / rotate, never on list/get
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastDeliveryAt,
        String lastDeliveryStatus
) {
    public String rawSigningSecret() {
        return signingSecret;
    }
}
