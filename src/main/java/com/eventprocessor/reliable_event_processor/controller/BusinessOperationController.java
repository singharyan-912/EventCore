package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.OperationRequest;
import com.eventprocessor.reliable_event_processor.dto.OperationResponse;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.handler.OperationHandlerRegistry;
import com.eventprocessor.reliable_event_processor.security.IngestionRateLimiter;
import com.eventprocessor.reliable_event_processor.service.OperationProcessingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/operations")
public class BusinessOperationController {

    private final OperationProcessingService operationProcessingService;
    private final OperationHandlerRegistry handlerRegistry;
    private final IngestionRateLimiter ingestionRateLimiter;

    public BusinessOperationController(OperationProcessingService operationProcessingService,
                                       OperationHandlerRegistry handlerRegistry,
                                       IngestionRateLimiter ingestionRateLimiter) {
        this.operationProcessingService = operationProcessingService;
        this.handlerRegistry = handlerRegistry;
        this.ingestionRateLimiter = ingestionRateLimiter;
    }

    @PostMapping
    public ResponseEntity<OperationResponse> processOperation(@Valid @RequestBody OperationRequest request,
                                                              HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        if (!ingestionRateLimiter.allowRequest(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        OperationResponse response = operationProcessingService.processOperation(request);
        if (EventStatus.RETRY_PENDING.name().equals(response.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/types")
    public ResponseEntity<Set<String>> getSupportedOperationTypes() {
        return ResponseEntity.ok(handlerRegistry.getSupportedOperationTypes());
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
