package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.BusinessRegistrationRequest;
import com.eventprocessor.reliable_event_processor.dto.BusinessRegistrationResponse;
import com.eventprocessor.reliable_event_processor.exception.RateLimitExceededException;
import com.eventprocessor.reliable_event_processor.security.RegistrationRateLimiter;
import com.eventprocessor.reliable_event_processor.service.BusinessRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST Controller for self-service business onboarding.
 * Rate limited per client IP to mitigate automated spam registrations.
 */
@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessRegistrationController {

    private final BusinessRegistrationService registrationService;
    private final RegistrationRateLimiter rateLimiter;

    public BusinessRegistrationController(BusinessRegistrationService registrationService,
                                          RegistrationRateLimiter rateLimiter) {
        this.registrationService = registrationService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<BusinessRegistrationResponse> registerBusiness(
            @Valid @RequestBody BusinessRegistrationRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        if (!rateLimiter.allowRegistration(clientIp)) {
            throw new RateLimitExceededException("Too many registration requests from this IP. Please try again later.");
        }

        BusinessRegistrationResponse response = registrationService.registerBusiness(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
