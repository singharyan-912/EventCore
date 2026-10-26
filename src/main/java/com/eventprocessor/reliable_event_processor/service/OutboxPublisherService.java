package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.OutboxProperties;
import com.eventprocessor.reliable_event_processor.dto.KafkaEventMessage;
import com.eventprocessor.reliable_event_processor.entity.OutboxEvent;
import com.eventprocessor.reliable_event_processor.entity.OutboxStatus;
import com.eventprocessor.reliable_event_processor.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties outboxProperties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisherService(OutboxEventRepository outboxEventRepository,
                                  OutboxProperties outboxProperties,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxProperties = outboxProperties;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Atomically claims a bounded batch of eligible outbox events in a short transaction.
     * Uses PostgreSQL 'FOR UPDATE SKIP LOCKED' so concurrent publishers never claim the same rows.
     */
    @Transactional
    public List<OutboxEvent> claimBatch(int batchSize) {
        OffsetDateTime staleTime = OffsetDateTime.now().minusSeconds(outboxProperties.getStaleClaimSeconds());
        List<OutboxEvent> batch = outboxEventRepository.findEligibleForPublishingWithLock(batchSize, staleTime);
        if (batch.isEmpty()) {
            return batch;
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (OutboxEvent event : batch) {
            event.setStatus(OutboxStatus.CLAIMED.name());
            event.setUpdatedAt(now);
        }
        return outboxEventRepository.saveAllAndFlush(batch);
    }

    /**
     * Publishes a single claimed event to Kafka.
     * EXECUTED OUTSIDE ANY DATABASE TRANSACTION to prevent holding database connections open.
     */
    public void publishEvent(OutboxEvent event) throws Exception {
        KafkaEventMessage message = new KafkaEventMessage(
                String.valueOf(event.getId()),
                event.getEventType(),
                event.getPartitionKey(),
                event.getIdempotencyKey(),
                event.getPayload(),
                Instant.now()
        );

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize KafkaEventMessage for event #" + event.getId(), e);
        }

        // Send to Kafka with partition key (e.g., resourceId) and await confirmation
        kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), messageJson)
                .get(5, TimeUnit.SECONDS);

        log.info("Published outbox event #{} to Kafka topic '{}' with key '{}'",
                event.getId(), event.getTopic(), event.getPartitionKey());
    }

    /**
     * Marks an outbox event as PUBLISHED in a short transaction.
     */
    @Transactional
    public void markPublished(Long eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            OffsetDateTime now = OffsetDateTime.now();
            event.setStatus(OutboxStatus.PUBLISHED.name());
            event.setPublishedAt(now);
            event.setUpdatedAt(now);
            outboxEventRepository.save(event);
            log.info("Outbox event #{} marked as PUBLISHED", eventId);
        });
    }

    /**
     * Handles publication failure in a short transaction.
     * Increments retry count; reverts to PENDING for retry or marks FAILED if exhausted.
     */
    @Transactional
    public void handlePublishFailure(Long eventId, Exception ex) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            int newRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(newRetryCount);
            event.setLastError(ex.getMessage());
            event.setUpdatedAt(OffsetDateTime.now());

            if (newRetryCount >= event.getMaxRetries()) {
                event.setStatus(OutboxStatus.FAILED.name());
                log.error("Outbox event #{} exceeded max retries ({}), marked as FAILED. Error: {}",
                        eventId, event.getMaxRetries(), ex.getMessage());
            } else {
                event.setStatus(OutboxStatus.PENDING.name());
                log.warn("Outbox event #{} send failed (attempt {}/{}), rescheduled for next poll. Error: {}",
                        eventId, newRetryCount, event.getMaxRetries(), ex.getMessage());
            }
            outboxEventRepository.save(event);
        });
    }
}
