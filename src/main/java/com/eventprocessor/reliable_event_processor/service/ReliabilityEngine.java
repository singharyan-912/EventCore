package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.RetryProperties;
import com.eventprocessor.reliable_event_processor.dto.EventResponse;
import com.eventprocessor.reliable_event_processor.entity.EventStatus;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.exception.IdempotencyConflictException;
import com.eventprocessor.reliable_event_processor.exception.TransientProcessingException;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Reusable Reliability Engine.
 *
 * Provides effectively-once business effect guarantees within the database transaction boundary,
 * coupled with persistent failure tracking, exponential backoff, and dead-letter queue support.
 */
@Component
public class ReliabilityEngine {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityEngine.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final RetryProperties retryProperties;

    public ReliabilityEngine(ProcessedEventRepository processedEventRepository,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager,
                             RetryProperties retryProperties) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.retryProperties = retryProperties;
    }

    /**
     * Executes an arbitrary business operation with idempotent reliability semantics.
     */
    public <R> R executeIdempotent(
            String idempotencyKey,
            String eventType,
            String resourceId,
            String payloadToHash,
            String rawPayloadJson,
            Class<R> responseType,
            Supplier<R> businessOperation,
            UnaryOperator<R> replayTransformer
    ) {
        String payloadHash = calculateSha256(payloadToHash);

        // Fast-path read outside transaction
        Optional<ProcessedEvent> existingEvent = processedEventRepository.findByIdempotencyKey(idempotencyKey);
        if (existingEvent.isPresent()) {
            return handleExistingEvent(existingEvent.get(), payloadHash, responseType, replayTransformer);
        }

        try {
            // Execute business mutation and event recording inside an atomic transaction
            return transactionTemplate.execute(status -> {
                // Double-check inside transaction boundary
                Optional<ProcessedEvent> inTxExisting = processedEventRepository.findByIdempotencyKey(idempotencyKey);
                if (inTxExisting.isPresent()) {
                    return handleExistingEvent(inTxExisting.get(), payloadHash, responseType, replayTransformer);
                }

                // Execute the business operation (e.g., deduct inventory)
                // If this throws a permanent exception (e.g. InsufficientInventory), transaction rolls back automatically
                R response = businessOperation.get();

                String serializedResponse;
                try {
                    serializedResponse = objectMapper.writeValueAsString(response);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Failed to serialize response payload for idempotency store", e);
                }

                ProcessedEvent event = new ProcessedEvent(
                        idempotencyKey,
                        eventType,
                        resourceId,
                        payloadHash,
                        EventStatus.PROCESSED.name(),
                        serializedResponse,
                        200
                );
                event.setRequestPayload(rawPayloadJson);
                event.setRetryCount(0);
                event.setMaxRetries(retryProperties.getMaxAttempts());
                event.setLastAttemptAt(OffsetDateTime.now());
                processedEventRepository.saveAndFlush(event);

                return response;
            });
        } catch (TransientProcessingException ex) {
            log.warn("Transient processing failure for idempotency key '{}': {}", idempotencyKey, ex.getMessage());
            return recordTransientFailure(idempotencyKey, eventType, resourceId, payloadHash, rawPayloadJson, ex);
        } catch (DataIntegrityViolationException ex) {
            // Handled concurrency race: another thread committed the same idempotencyKey concurrently
            log.info("Detected concurrent duplicate submission for idempotency key: {}", idempotencyKey);
            ProcessedEvent winnerEvent = processedEventRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            return handleExistingEvent(winnerEvent, payloadHash, responseType, replayTransformer);
        }
    }

    @SuppressWarnings("unchecked")
    private <R> R recordTransientFailure(String idempotencyKey, String eventType, String resourceId,
                                         String payloadHash, String rawPayloadJson, Exception ex) {
        return transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            Duration backoff = retryProperties.calculateBackoff(1);
            OffsetDateTime nextRetry = now.plus(backoff);

            ProcessedEvent event = new ProcessedEvent();
            event.setIdempotencyKey(idempotencyKey);
            event.setEventType(eventType);
            event.setResourceId(resourceId);
            event.setRequestPayloadHash(payloadHash);
            event.setRequestPayload(rawPayloadJson);
            event.setStatus(EventStatus.RETRY_PENDING.name());
            event.setRetryCount(1);
            event.setMaxRetries(retryProperties.getMaxAttempts());
            event.setLastFailureReason(ex.getMessage());
            event.setNextRetryAt(nextRetry);
            event.setLastAttemptAt(now);
            event.setUpdatedAt(now);
            processedEventRepository.saveAndFlush(event);

            EventResponse response = new EventResponse(
                    idempotencyKey,
                    eventType,
                    resourceId,
                    null,
                    null,
                    EventStatus.RETRY_PENDING.name(),
                    false,
                    now.toInstant(),
                    1,
                    nextRetry,
                    "Event accepted; transient failure encountered and scheduled for retry: " + ex.getMessage()
            );
            return (R) response;
        });
    }

    @SuppressWarnings("unchecked")
    private <R> R handleExistingEvent(ProcessedEvent event,
                                      String currentPayloadHash,
                                      Class<R> responseType,
                                      UnaryOperator<R> replayTransformer) {
        // Case C: Same key, different payload -> 409 Conflict
        if (!event.getRequestPayloadHash().equals(currentPayloadHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency key '%s' has already been used with a different request payload"
                            .formatted(event.getIdempotencyKey())
            );
        }

        // Case B: Same key, same payload -> Check status
        if (EventStatus.PROCESSED.name().equals(event.getStatus())) {
            try {
                R cachedResponse = objectMapper.readValue(event.getResponsePayload(), responseType);
                return replayTransformer != null ? replayTransformer.apply(cachedResponse) : cachedResponse;
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize cached response payload", e);
            }
        }

        // If RETRY_PENDING, PROCESSING, or DEAD_LETTER
        Instant processedInstant = event.getLastAttemptAt() != null
                ? event.getLastAttemptAt().toInstant()
                : (event.getCreatedAt() != null ? event.getCreatedAt().toInstant() : Instant.now());

        String message = EventStatus.DEAD_LETTER.name().equals(event.getStatus())
                ? "Event in DEAD_LETTER queue: " + event.getLastFailureReason()
                : "Event processing is currently " + event.getStatus() + "; automatic retry scheduled.";

        EventResponse replayPending = new EventResponse(
                event.getIdempotencyKey(),
                event.getEventType(),
                event.getResourceId(),
                null,
                null,
                event.getStatus(),
                true, // idempotent replay
                processedInstant,
                event.getRetryCount(),
                event.getNextRetryAt(),
                message
        );
        return (R) replayPending;
    }

    public static String calculateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
