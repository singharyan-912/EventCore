package com.eventprocessor.reliable_event_processor.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWebhookRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @NotBlank(message = "destinationUrl must not be blank")
        String destinationUrl,

        @NotBlank(message = "subscribedEvents must not be blank")
        String subscribedEvents
) {}
