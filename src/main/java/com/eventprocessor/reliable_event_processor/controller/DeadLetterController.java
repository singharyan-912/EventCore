package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.DeadLetterResponse;
import com.eventprocessor.reliable_event_processor.dto.PageResponse;
import com.eventprocessor.reliable_event_processor.service.DeadLetterService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Administrative endpoints for dead-letter event management.
 *
 * SECURITY: Accessible by ADMIN and OPERATOR roles.
 * Supports Bearer JWT tokens and backward-compatible X-Admin-API-Key header.
 */
@RestController
@RequestMapping("/api/v1/admin/dead-letters")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    public DeadLetterController(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    /**
     * List all dead-letter events with pagination.
     * GET /api/v1/admin/dead-letters?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<PageResponse<DeadLetterResponse>> listDeadLetters(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (apiKey != null) {
            deadLetterService.validateApiKey(apiKey);
        }
        return ResponseEntity.ok(deadLetterService.listDeadLetterEvents(page, size));
    }

    /**
     * Retrieve details of a specific dead-letter event.
     * GET /api/v1/admin/dead-letters/{idempotencyKey}
     */
    @GetMapping("/{idempotencyKey}")
    public ResponseEntity<DeadLetterResponse> getDeadLetter(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey,
            @PathVariable String idempotencyKey) {
        if (apiKey != null) {
            deadLetterService.validateApiKey(apiKey);
        }
        return ResponseEntity.ok(deadLetterService.getDeadLetterEvent(idempotencyKey));
    }

    /**
     * Request operator-initiated reprocessing of a dead-letter event.
     * POST /api/v1/admin/dead-letters/{idempotencyKey}/reprocess
     * Schedules the event for immediate retry by the RetryWorker.
     * Does NOT re-apply any business effect that already succeeded.
     */
    @PostMapping("/{idempotencyKey}/reprocess")
    public ResponseEntity<DeadLetterResponse> requestReprocessing(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey,
            @PathVariable String idempotencyKey,
            Authentication authentication) {
        if (apiKey != null) {
            deadLetterService.validateApiKey(apiKey);
        }
        String principal = (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : "admin-api-key";
        return ResponseEntity.ok(deadLetterService.requestReprocessing(idempotencyKey, principal));
    }
}
