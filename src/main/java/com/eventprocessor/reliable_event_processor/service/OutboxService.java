package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.OutboxProperties;
import com.eventprocessor.reliable_event_processor.entity.OutboxEvent;
import com.eventprocessor.reliable_event_processor.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for recording outbox events within active database transactions.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties outboxProperties;

    public OutboxService(OutboxEventRepository outboxEventRepository,
                         OutboxProperties outboxProperties) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxProperties = outboxProperties;
    }

    /**
     * Persists an outbox event within the caller's ongoing database transaction (MANDATORY).
     * Guarantees atomic persistence with business entity updates and processed_events state.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordOutboxEvent(String idempotencyKey,
                                         String eventType,
                                         String partitionKey,
                                         String payload) {
        OutboxEvent outboxEvent = new OutboxEvent(
                idempotencyKey,
                eventType,
                outboxProperties.getTopic(),
                partitionKey,
                payload
        );
        outboxEvent.setMaxRetries(outboxProperties.getMaxRetries());
        OutboxEvent saved = outboxEventRepository.save(outboxEvent);
        log.info("Recorded outbox event #{} for idempotencyKey='{}', topic='{}'",
                saved.getId(), idempotencyKey, outboxProperties.getTopic());
        return saved;
    }
}
