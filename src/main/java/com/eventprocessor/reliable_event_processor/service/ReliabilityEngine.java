package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.exception.IdempotencyConflictException;
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
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Reusable Reliability Engine.
 *
 * Provides effectively-once business effect guarantees within the database transaction boundary.
 *
 * NOTE ON DISTRIBUTED SYSTEMS:
 * True "exactly-once" delivery across distributed networks is impossible under the Two Generals' Theorem.
 * Instead, this engine enforces "effectively-once" business semantics by coupling the business mutation
 * and event persistence inside an atomic PostgreSQL ACID transaction guarded by unique constraints
 * and pessimistic row locking.
 *
 * RETRY POLICY FOR REJECTIONS:
 * Temporary domain rejections (e.g., insufficient inventory, missing resources) intentionally roll back
 * the entire transaction without creating an idempotency record. This ensures clients may retry the same
 * idempotency key once resources are replenished without being permanently locked out by a cached failure.
 */
@Component
public class ReliabilityEngine {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityEngine.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ReliabilityEngine(ProcessedEventRepository processedEventRepository,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Executes an arbitrary business operation with idempotent reliability semantics.
     *
     * @param idempotencyKey    Unique client-specified idempotency key
     * @param eventType         Event type identifier
     * @param resourceId        Resource identifier
     * @param payloadToHash     Deterministic string representation of the request payload
     * @param responseType      Class of the expected response object
     * @param businessOperation Business logic supplier executed inside the transaction
     * @param replayTransformer Transforms cached response to flag it as an idempotent replay
     * @param <R>               Type of the response DTO
     * @return Execution result
     */
    public <R> R executeIdempotent(
            String idempotencyKey,
            String eventType,
            String resourceId,
            String payloadToHash,
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
                // If this throws an exception, transactionTemplate rolls back automatically
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
                        "PROCESSED",
                        serializedResponse,
                        200
                );
                processedEventRepository.saveAndFlush(event);

                return response;
            });
        } catch (DataIntegrityViolationException ex) {
            // Handled concurrency race: another thread committed the same idempotencyKey concurrently
            log.info("Detected concurrent duplicate submission for idempotency key: {}", idempotencyKey);
            ProcessedEvent winnerEvent = processedEventRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            return handleExistingEvent(winnerEvent, payloadHash, responseType, replayTransformer);
        }
    }

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

        // Case B: Same key, same payload -> Idempotent replay
        try {
            R cachedResponse = objectMapper.readValue(event.getResponsePayload(), responseType);
            return replayTransformer != null ? replayTransformer.apply(cachedResponse) : cachedResponse;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached response payload", e);
        }
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
