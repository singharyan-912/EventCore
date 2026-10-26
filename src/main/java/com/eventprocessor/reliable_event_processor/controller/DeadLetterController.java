package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.DeadLetterResponse;
import com.eventprocessor.reliable_event_processor.dto.PageResponse;
import com.eventprocessor.reliable_event_processor.service.DeadLetterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Administrative endpoints for dead-letter event management.
 *
 * SECURITY: All endpoints require the X-Admin-API-Key header.
 *
 * LOCAL DEVELOPMENT SETUP:
 *   Default key: "admin-secret-key-12345" (configured in AdminSecurityProperties)
 *   Override via: event-processor.admin.api-key=<your-key> in application.properties
 *   or environment variable: EVENT_PROCESSOR_ADMIN_API_KEY=<your-key>
 *
 * Example header: X-Admin-API-Key: admin-secret-key-12345
 */
@RestController
@RequestMapping("/api/v1/admin/dead-letters")
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
            @RequestHeader("X-Admin-API-Key") String apiKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        deadLetterService.validateApiKey(apiKey);
        return ResponseEntity.ok(deadLetterService.listDeadLetterEvents(page, size));
    }

    /**
     * Retrieve details of a specific dead-letter event.
     * GET /api/v1/admin/dead-letters/{idempotencyKey}
     */
    @GetMapping("/{idempotencyKey}")
    public ResponseEntity<DeadLetterResponse> getDeadLetter(
            @RequestHeader("X-Admin-API-Key") String apiKey,
            @PathVariable String idempotencyKey) {
        deadLetterService.validateApiKey(apiKey);
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
            @RequestHeader("X-Admin-API-Key") String apiKey,
            @PathVariable String idempotencyKey) {
        deadLetterService.validateApiKey(apiKey);
        return ResponseEntity.ok(deadLetterService.requestReprocessing(idempotencyKey, "admin-api-key"));
    }
}
