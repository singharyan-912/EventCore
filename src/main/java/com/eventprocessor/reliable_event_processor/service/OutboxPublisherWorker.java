package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.OutboxProperties;
import com.eventprocessor.reliable_event_processor.entity.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled worker that polls for PENDING outbox events and publishes them to Kafka.
 *
 * CONCURRENCY SAFETY:
 * Batch claiming uses PostgreSQL 'FOR UPDATE SKIP LOCKED' in a short transaction,
 * ensuring competing workers or multiple instances never claim or publish the same record.
 *
 * TRANSACTION ISOLATION:
 * The Kafka network call is executed strictly outside any database transaction.
 */
@Component
public class OutboxPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);

    private final OutboxPublisherService outboxPublisherService;
    private final OutboxProperties outboxProperties;

    public OutboxPublisherWorker(OutboxPublisherService outboxPublisherService,
                                 OutboxProperties outboxProperties) {
        this.outboxPublisherService = outboxPublisherService;
        this.outboxProperties = outboxProperties;
    }

    @Scheduled(fixedDelayString = "${event-processor.outbox.poll-interval-ms:2000}")
    public void processOutboxEvents() {
        if (!outboxProperties.isEnabled()) {
            return;
        }

        List<OutboxEvent> batch;
        try {
            batch = outboxPublisherService.claimBatch(outboxProperties.getBatchSize());
        } catch (Exception ex) {
            log.error("OutboxPublisherWorker: error claiming batch: {}", ex.getMessage(), ex);
            return;
        }

        if (batch.isEmpty()) {
            return;
        }

        log.debug("OutboxPublisherWorker: claimed {} outbox event(s) for publishing", batch.size());

        for (OutboxEvent event : batch) {
            try {
                outboxPublisherService.publishEvent(event);
                outboxPublisherService.markPublished(event.getId());
            } catch (Exception ex) {
                outboxPublisherService.handlePublishFailure(event.getId(), ex);
            }
        }
    }
}
