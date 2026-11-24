package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.RetryProperties;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.handler.BusinessOperationHandler;
import com.eventprocessor.reliable_event_processor.handler.InventoryDeductHandler;
import com.eventprocessor.reliable_event_processor.handler.OperationHandlerRegistry;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Handles business re-execution for retry and reprocessing operations.
 * Separated from RetryWorker to keep scheduling concerns apart from business logic.
 */
@Service
public class RetryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutionService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OperationHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;
    private final RetryProperties retryProperties;
    private final TransientFailureSimulator failureSimulator;
    private final OutboxService outboxService;
    private final EventMetricsService metricsService;
    private final com.eventprocessor.reliable_event_processor.repository.ExternalIntegrationRepository integrationRepository;
    private final OperationalAuditService auditService;
    private final com.eventprocessor.reliable_event_processor.config.ReliabilityEnhancementProperties enhancementProperties;

    public RetryExecutionService(ProcessedEventRepository processedEventRepository,
                                 OperationHandlerRegistry handlerRegistry,
                                 ObjectMapper objectMapper,
                                 RetryProperties retryProperties,
                                 TransientFailureSimulator failureSimulator,
                                 OutboxService outboxService,
                                 EventMetricsService metricsService,
                                 com.eventprocessor.reliable_event_processor.repository.ExternalIntegrationRepository integrationRepository,
                                 OperationalAuditService auditService,
                                 com.eventprocessor.reliable_event_processor.config.ReliabilityEnhancementProperties enhancementProperties) {
        this.processedEventRepository = processedEventRepository;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.retryProperties = retryProperties;
        this.failureSimulator = failureSimulator;
        this.outboxService = outboxService;
        this.metricsService = metricsService;
        this.integrationRepository = integrationRepository;
        this.auditService = auditService;
        this.enhancementProperties = enhancementProperties;
    }

    /**
     * Atomically claims a RETRY_PENDING event (sets it to PROCESSING) and executes the business operation.
     * Uses pessimistic write lock to prevent concurrent workers from executing the same event.
     *
     * Business failures (InsufficientInventory, ResourceNotFound) are permanent and NOT retried.
     * Transient failures schedule another retry or move to DEAD_LETTER on exhaustion.
     */
    @Transactional
    public void executeRetry(ProcessedEvent event) {
        String tenantId = (event.getTenantId() != null && !event.getTenantId().isBlank())
                ? event.getTenantId()
                : "DEFAULT_BUSINESS";

        com.eventprocessor.reliable_event_processor.security.TenantContext.setTenantId(tenantId);
        try {
            // Re-acquire lock inside transaction for concurrency safety
            ProcessedEvent locked = processedEventRepository
                    .findByTenantIdAndIdempotencyKeyForUpdate(tenantId, event.getIdempotencyKey())
                    .orElse(null);

            if (locked == null) {
                log.warn("Event disappeared during retry claim: [tenant={}, key={}]", tenantId, event.getIdempotencyKey());
                return;
            }

            // Only process if still RETRY_PENDING (another worker may have already claimed it)
            if (!EventStatus.RETRY_PENDING.name().equals(locked.getStatus())) {
                log.debug("Event {} no longer RETRY_PENDING (status={}), skipping",
                        locked.getIdempotencyKey(), locked.getStatus());
                return;
            }

            // If event has already reached or exceeded max retries, move directly to DEAD_LETTER
            if (locked.getRetryCount() >= locked.getMaxRetries()) {
                log.warn("Event {} already reached max retries ({}), moving to DEAD_LETTER",
                        locked.getIdempotencyKey(), locked.getMaxRetries());
                markDeadLetter(locked, "Max retries (" + locked.getMaxRetries() + ") exhausted");
                return;
            }

            // Mark as PROCESSING to claim it with lease expiration
            locked.setStatus(EventStatus.PROCESSING.name());
            OffsetDateTime now = OffsetDateTime.now();
            locked.setLastAttemptAt(now);
            locked.setProcessingLeaseExpiresAt(now.plusSeconds(enhancementProperties.getLease().getProcessingLeaseSeconds()));
            processedEventRepository.save(locked);

            metricsService.recordRetryAttempt(locked.getEventType());

            try {
                executeBusinessOperation(locked);
            } catch (Exception ex) {
                BusinessOperationHandler<?> handler = handlerRegistry.getHandler(locked.getEventType()).orElse(null);
                boolean retryable = (handler != null)
                        ? handler.isRetryable(ex)
                        : !(ex instanceof com.eventprocessor.reliable_event_processor.exception.InsufficientInventoryException
                            || ex instanceof com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException
                            || ex instanceof com.eventprocessor.reliable_event_processor.exception.OperationValidationException);

                if (!retryable) {
                    log.warn("Permanent business failure [tenant={}, event_type={}, idempotency_key={}]: {}",
                            locked.getTenantId(), locked.getEventType(), locked.getIdempotencyKey(), ex.getMessage());
                    markDeadLetter(locked, "Permanent failure (will not retry): " + ex.getMessage(), "PERMANENT_FAILURE");
                } else {
                    log.warn("Transient failure [tenant={}, event_type={}, idempotency_key={}, attempt={}/{}]: {}",
                            locked.getTenantId(), locked.getEventType(), locked.getIdempotencyKey(), locked.getRetryCount() + 1, locked.getMaxRetries(), ex.getMessage());
                    scheduleRetryOrDeadLetter(locked, ex.getMessage());
                }
            }
        } finally {
            com.eventprocessor.reliable_event_processor.security.TenantContext.clear();
        }
    }

    private void executeBusinessOperation(ProcessedEvent event) {
        // Simulate transient failure if simulation is active
        failureSimulator.maybeSimulateTransientFailure(event.getIdempotencyKey(), event.getResourceId(), 2);

        BusinessOperationHandler<?> handler = handlerRegistry.getRequiredHandler(event.getEventType());

        JsonNode payloadNode;
        try {
            payloadNode = (event.getRequestPayload() != null)
                    ? objectMapper.readTree(event.getRequestPayload())
                    : objectMapper.createObjectNode();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse stored request payload for event: " + event.getIdempotencyKey(), e);
        }

        Object result = handler.executeRetry(event.getTenantId(), event.getIdempotencyKey(), event.getResourceId(), payloadNode);
        JsonNode resultNode = handler.serializeResultUntyped(result);

        String serializedResponse;
        if (InventoryDeductHandler.OPERATION_TYPE.equals(event.getEventType()) && resultNode.has("deductedQuantity")) {
            ObjectNode responseNode = objectMapper.createObjectNode();
            responseNode.put("idempotencyKey", event.getIdempotencyKey());
            responseNode.put("eventType", event.getEventType());
            responseNode.put("resourceId", event.getResourceId());
            responseNode.put("deductedQuantity", resultNode.get("deductedQuantity").asInt());
            responseNode.put("remainingQuantity", resultNode.get("remainingQuantity").asInt());
            responseNode.put("status", EventStatus.PROCESSED.name());
            responseNode.put("idempotentReplay", false);
            serializedResponse = responseNode.toString();
        } else {
            ObjectNode opNode = objectMapper.createObjectNode();
            opNode.put("idempotencyKey", event.getIdempotencyKey());
            opNode.put("operationType", event.getEventType());
            opNode.put("resourceId", event.getResourceId());
            opNode.put("tenantId", event.getTenantId());
            opNode.put("status", EventStatus.PROCESSED.name());
            opNode.put("idempotentReplay", false);
            opNode.set("result", resultNode);
            serializedResponse = opNode.toString();
        }

        event.setResponsePayload(serializedResponse);
        event.setResponseStatusCode(200);
        event.setStatus(EventStatus.PROCESSED.name());
        event.setLastFailureReason(null);
        event.setNextRetryAt(null);
        processedEventRepository.save(event);

        // Record in outbox in the same transaction
        outboxService.recordOutboxEvent(
                event.getTenantId(),
                event.getIdempotencyKey(),
                event.getEventType(),
                event.getResourceId(),
                event.getResponsePayload()
        );

        log.info("Retry succeeded [event_type={}, idempotency_key={}, attempt={}]",
                event.getEventType(), event.getIdempotencyKey(), event.getRetryCount());
    }

    private void scheduleRetryOrDeadLetter(ProcessedEvent event, String failureReason) {
        int newCount = event.getRetryCount() + 1;
        event.setRetryCount(newCount);
        event.setLastFailureReason(failureReason);

        if (newCount >= event.getMaxRetries()) {
            markDeadLetter(event, "Max retries (" + event.getMaxRetries() + ") exhausted. Last error: " + failureReason, "MAX_RETRIES_EXHAUSTED");
        } else {
            java.time.Duration backoff = retryProperties.calculateBackoff(newCount + 1);
            event.setNextRetryAt(OffsetDateTime.now().plus(backoff));
            event.setStatus(EventStatus.RETRY_PENDING.name());
            processedEventRepository.save(event);
            log.info("Retry scheduled [event_type={}, idempotency_key={}, attempt={}/{}, next_retry_at={}]",
                    event.getEventType(), event.getIdempotencyKey(), newCount + 1, event.getMaxRetries(), event.getNextRetryAt());
        }
    }

    private void markDeadLetter(ProcessedEvent event, String reason) {
        markDeadLetter(event, reason, "EXHAUSTED");
    }

    private void markDeadLetter(ProcessedEvent event, String reason, String reasonCategory) {
        event.setStatus(EventStatus.DEAD_LETTER.name());
        event.setLastFailureReason(reason);
        event.setNextRetryAt(null);
        processedEventRepository.save(event);

        metricsService.recordDeadLetter(event.getEventType(), reasonCategory);
        log.warn("Event moved to DEAD_LETTER [event_type={}, idempotency_key={}, reason_category={}, reason={}]: {}",
                event.getEventType(), event.getIdempotencyKey(), reasonCategory, reason, reason);
    }

    /**
     * Safely recovers an abandoned or stuck PROCESSING event whose lease has expired.
     * Distinguishes local processing (recover to RETRY_PENDING) from external integrations (move to RECONCILIATION_REQUIRED).
     */
    @Transactional
    public void recoverStuckEvent(ProcessedEvent event) {
        String tenantId = (event.getTenantId() != null && !event.getTenantId().isBlank())
                ? event.getTenantId()
                : "DEFAULT_BUSINESS";

        com.eventprocessor.reliable_event_processor.security.TenantContext.setTenantId(tenantId);
        try {
            ProcessedEvent locked = processedEventRepository
                    .findByTenantIdAndIdempotencyKeyForUpdate(tenantId, event.getIdempotencyKey())
                    .orElse(null);

            if (locked == null || !EventStatus.PROCESSING.name().equals(locked.getStatus())) {
                return;
            }

            OffsetDateTime now = OffsetDateTime.now();
            boolean isExternalIntegration = integrationRepository.findByTenantIdAndStatus(tenantId, "ACTIVE")
                    .stream()
                    .anyMatch(i -> i.supportsOperation(locked.getEventType()));

            if (isExternalIntegration) {
                // External operations that timed out or got stuck cannot be blindly retried
                log.warn("Recovering stuck external operation [tenant={}, key={}] -> RECONCILIATION_REQUIRED",
                        tenantId, locked.getIdempotencyKey());
                locked.setStatus(EventStatus.RECONCILIATION_REQUIRED.name());
                locked.setNextRetryAt(null);
                locked.setProcessingLeaseExpiresAt(null);
                locked.setLastFailureReason("Worker processing lease expired during external operation execution; ambiguous outcome requires reconciliation.");
                locked.setUpdatedAt(now);
                processedEventRepository.save(locked);

                auditService.recordAuditLog(
                        tenantId,
                        "STUCK_EVENT_RECOVERY",
                        "event-" + locked.getIdempotencyKey(),
                        "RECONCILIATION_REQUIRED",
                        null,
                        "SYSTEM",
                        "Recovered stuck external operation to RECONCILIATION_REQUIRED"
                );
            } else {
                // Local operation lease expired -> safely reschedule for retry
                log.warn("Recovering stuck local operation [tenant={}, key={}] -> RETRY_PENDING",
                        tenantId, locked.getIdempotencyKey());
                locked.setProcessingLeaseExpiresAt(null);
                scheduleRetryOrDeadLetter(locked, "Worker processing lease expired while in PROCESSING state; scheduled for retry.");
                auditService.recordAuditLog(
                        tenantId,
                        "STUCK_EVENT_RECOVERY",
                        "event-" + locked.getIdempotencyKey(),
                        locked.getStatus(),
                        null,
                        "SYSTEM",
                        "Recovered abandoned local processing lease to " + locked.getStatus()
                );
            }
        } finally {
            com.eventprocessor.reliable_event_processor.security.TenantContext.clear();
        }
    }
}
