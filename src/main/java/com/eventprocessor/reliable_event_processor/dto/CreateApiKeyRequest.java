package com.eventprocessor.reliable_event_processor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateApiKeyRequest(
        @NotBlank(message = "Key name must not be blank")
        @Size(min = 2, max = 100, message = "Key name must be between 2 and 100 characters")
        String name,

        OffsetDateTime expiresAt
) {}
