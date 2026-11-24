package com.eventprocessor.reliable_event_processor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessRegistrationRequest(
        @NotBlank(message = "Business name is required")
        @Size(min = 2, max = 100, message = "Business name must be between 2 and 100 characters")
        String businessName,

        @NotBlank(message = "Owner or contact name is required")
        @Size(min = 2, max = 100, message = "Owner name must be between 2 and 100 characters")
        String ownerName,

        @NotBlank(message = "Business email address is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password,

        String phone
) {}
