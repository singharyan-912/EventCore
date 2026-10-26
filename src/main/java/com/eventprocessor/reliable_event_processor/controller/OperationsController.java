package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.config.AdminSecurityProperties;
import com.eventprocessor.reliable_event_processor.dto.*;
import com.eventprocessor.reliable_event_processor.entity.OperationalAuditLog;
import com.eventprocessor.reliable_event_processor.exception.UnauthorizedException;
import com.eventprocessor.reliable_event_processor.service.OperationalAuditService;
import com.eventprocessor.reliable_event_processor.service.OperationsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * Operations and Observability REST API.
 *
 * Provides inspection, query filtering, database aggregations, and operational audit history.
 * Bounded pagination protects system memory and database performance.
 */
@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {

    private final OperationsService operationsService;
    private final OperationalAuditService auditService;
    private final AdminSecurityProperties adminSecurityProperties;

    public OperationsController(OperationsService operationsService,
                                OperationalAuditService auditService,
                                AdminSecurityProperties adminSecurityProperties) {
        this.operationsService = operationsService;
        this.auditService = auditService;
        this.adminSecurityProperties = adminSecurityProperties;
    }

    private void validateApiKeyIfPresent(String apiKey) {
        if (apiKey != null && !apiKey.equals(adminSecurityProperties.getApiKey())) {
            throw new UnauthorizedException("Invalid X-Admin-API-Key header");
        }
    }

    /**
     * Search and filter events with pagination.
     * GET /api/v1/operations/events?status=PROCESSED&page=0&size=20
     */
    @GetMapping("/events")
    public ResponseEntity<PageResponse<EventDetailResponse>> searchEvents(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String idempotencyKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateApiKeyIfPresent(apiKey);
        return ResponseEntity.ok(operationsService.searchEvents(
                status, eventType, resourceId, idempotencyKey, from, to, page, size));
    }

    /**
     * Retrieve single event detail by idempotency key with outbox publication state.
     * GET /api/v1/operations/events/{idempotencyKey}
     */
    @GetMapping("/events/{idempotencyKey}")
    public ResponseEntity<EventDetailResponse> getEventDetail(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey,
            @PathVariable String idempotencyKey) {
        validateApiKeyIfPresent(apiKey);
        return ResponseEntity.ok(operationsService.getEventDetail(idempotencyKey));
    }

    /**
     * Retrieve summary counts of events by status directly from database.
     * GET /api/v1/operations/events/summary
     */
    @GetMapping("/events/summary")
    public ResponseEntity<EventSummaryResponse> getEventSummary(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey) {
        validateApiKeyIfPresent(apiKey);
        return ResponseEntity.ok(operationsService.getEventSummary());
    }

    /**
     * Retrieve summary counts of outbox events by status and retry state.
     * GET /api/v1/operations/outbox/summary
     */
    @GetMapping("/outbox/summary")
    public ResponseEntity<OutboxSummaryResponse> getOutboxSummary(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey) {
        validateApiKeyIfPresent(apiKey);
        return ResponseEntity.ok(operationsService.getOutboxSummary());
    }

    /**
     * Retrieve unified operational metrics: runtime in-memory metrics + durable database counts.
     * GET /api/v1/operations/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<OperationsMetricsResponse> getOperationsMetrics(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey) {
        validateApiKeyIfPresent(apiKey);
        return ResponseEntity.ok(operationsService.getOperationsMetrics());
    }

    /**
     * Query operational audit logs with pagination and optional filtering.
     * GET /api/v1/operations/audit-logs?page=0&size=20
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<PageResponse<OperationalAuditLogResponse>> getAuditLogs(
            @RequestHeader(value = "X-Admin-API-Key", required = false) String apiKey,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String targetIdentifier,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateApiKeyIfPresent(apiKey);
        var pageResult = auditService.queryAuditLogs(actionType, targetIdentifier, outcome, page, size)
                .map(this::toAuditResponse);
        return ResponseEntity.ok(PageResponse.from(pageResult));
    }

    private OperationalAuditLogResponse toAuditResponse(OperationalAuditLog log) {
        return new OperationalAuditLogResponse(
                log.getId(),
                log.getActionType(),
                log.getTargetIdentifier(),
                log.getOutcome(),
                log.getFailureReason(),
                log.getPrincipal(),
                log.getDetails(),
                log.getTimestamp()
        );
    }
}
