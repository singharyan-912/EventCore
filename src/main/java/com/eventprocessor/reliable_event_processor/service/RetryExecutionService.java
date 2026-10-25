package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.RetryProperties;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.entity.Inventory;
import com.eventprocessor.reliable_event_processor.exception.InsufficientInventoryException;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final RetryProperties retryProperties;
    private final TransientFailureSimulator failureSimulator;

    public RetryExecutionService(ProcessedEventRepository processedEventRepository,
                                  InventoryService inventoryService,
                                  ObjectMapper objectMapper,
                                  RetryProperties retryProperties,
                                  TransientFailureSimulator failureSimulator) {
        this.processedEventRepository = processedEventRepository;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.retryProperties = retryProperties;
        this.failureSimulator = failureSimulator;
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
        // Re-acquire lock inside transaction for concurrency safety
        ProcessedEvent locked = processedEventRepository
                .findByIdempotencyKeyForUpdate(event.getIdempotencyKey())
                .orElse(null);

        if (locked == null) {
            log.warn("Event disappeared during retry claim: {}", event.getIdempotencyKey());
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

        // Mark as PROCESSING to claim it
        locked.setStatus(EventStatus.PROCESSING.name());
        locked.setLastAttemptAt(OffsetDateTime.now());
        processedEventRepository.save(locked);

        try {
            executeBusinessOperation(locked);
        } catch (InsufficientInventoryException | ResourceNotFoundException permanentEx) {
            // Permanent business failure — do NOT retry; move to DEAD_LETTER immediately
            log.warn("Permanent business failure for event {}: {}", locked.getIdempotencyKey(), permanentEx.getMessage());
            markDeadLetter(locked, "Permanent failure (will not retry): " + permanentEx.getMessage());
        } catch (RuntimeException transientEx) {
            // Transient failure (including TransientProcessingException) —
            // schedule retry or move to DEAD_LETTER if exhausted
            log.warn("Transient failure on attempt {} for event {}: {}",
                    locked.getRetryCount() + 1, locked.getIdempotencyKey(), transientEx.getMessage());
            scheduleRetryOrDeadLetter(locked, transientEx.getMessage());
        }
    }

    private void executeBusinessOperation(ProcessedEvent event) {
        // Simulate transient failure if simulation is active
        failureSimulator.maybeSimulateTransientFailure(event.getIdempotencyKey(), event.getResourceId(), 2);

        // Parse quantity from stored request payload
        int quantity = extractQuantityFromPayload(event);

        Inventory updated = inventoryService.deductInventory(event.getResourceId(), quantity);

        // Build minimal response payload
        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put("idempotencyKey", event.getIdempotencyKey());
        responseNode.put("eventType", event.getEventType());
        responseNode.put("resourceId", event.getResourceId());
        responseNode.put("deductedQuantity", quantity);
        responseNode.put("remainingQuantity", updated.getAvailableQuantity());
        responseNode.put("status", EventStatus.PROCESSED.name());
        responseNode.put("idempotentReplay", false);

        try {
            event.setResponsePayload(objectMapper.writeValueAsString(responseNode));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize retry response", e);
        }
        event.setResponseStatusCode(200);
        event.setStatus(EventStatus.PROCESSED.name());
        event.setLastFailureReason(null);
        event.setNextRetryAt(null);
        processedEventRepository.save(event);

        log.info("Retry succeeded for event {} (attempt {})", event.getIdempotencyKey(), event.getRetryCount());
    }

    private void scheduleRetryOrDeadLetter(ProcessedEvent event, String failureReason) {
        int newCount = event.getRetryCount() + 1;
        event.setRetryCount(newCount);
        event.setLastFailureReason(failureReason);

        if (newCount >= event.getMaxRetries()) {
            markDeadLetter(event, "Max retries (" + event.getMaxRetries() + ") exhausted. Last error: " + failureReason);
        } else {
            java.time.Duration backoff = retryProperties.calculateBackoff(newCount + 1);
            event.setNextRetryAt(OffsetDateTime.now().plus(backoff));
            event.setStatus(EventStatus.RETRY_PENDING.name());
            processedEventRepository.save(event);
            log.info("Event {} scheduled for retry #{} at {}", event.getIdempotencyKey(), newCount + 1, event.getNextRetryAt());
        }
    }

    private void markDeadLetter(ProcessedEvent event, String reason) {
        event.setStatus(EventStatus.DEAD_LETTER.name());
        event.setLastFailureReason(reason);
        event.setNextRetryAt(null);
        processedEventRepository.save(event);
        log.warn("Event {} moved to DEAD_LETTER: {}", event.getIdempotencyKey(), reason);
    }

    private int extractQuantityFromPayload(ProcessedEvent event) {
        if (event.getRequestPayload() == null) {
            throw new IllegalStateException("No stored request payload for event: " + event.getIdempotencyKey());
        }
        try {
            var node = objectMapper.readTree(event.getRequestPayload());
            return node.get("quantity").asInt();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse quantity from stored payload: " + e.getMessage(), e);
        }
    }
}
