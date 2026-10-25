package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.config.WorkerProperties;
import com.eventprocessor.reliable_event_processor.entity.ProcessedEvent;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled worker that polls for RETRY_PENDING events and re-executes them.
 *
 * CONCURRENCY SAFETY:
 * Uses PostgreSQL's "SELECT ... FOR UPDATE SKIP LOCKED" so that multiple worker instances
 * (e.g., on different servers or threads) never process the same event simultaneously.
 * Each worker claims a separate batch of rows.
 *
 * RESTART SAFETY:
 * Retry schedules are persisted in the database. Restarting the application does not
 * lose any pending retry records.
 *
 * POLLING INTERVAL:
 * Configurable via event-processor.worker.poll-interval-ms (default: 2000ms).
 * No thread sleeping for backoff durations — the worker simply skips events not yet due.
 */
@Component
public class RetryWorker {

    private static final Logger log = LoggerFactory.getLogger(RetryWorker.class);

    private final ProcessedEventRepository processedEventRepository;
    private final RetryExecutionService retryExecutionService;
    private final WorkerProperties workerProperties;

    public RetryWorker(ProcessedEventRepository processedEventRepository,
                       RetryExecutionService retryExecutionService,
                       WorkerProperties workerProperties) {
        this.processedEventRepository = processedEventRepository;
        this.retryExecutionService = retryExecutionService;
        this.workerProperties = workerProperties;
    }

    @Scheduled(fixedDelayString = "${event-processor.worker.poll-interval-ms:2000}")
    public void processPendingRetries() {
        if (!workerProperties.isEnabled()) {
            return;
        }

        List<ProcessedEvent> eligible = fetchEligibleBatch();
        if (eligible.isEmpty()) {
            return;
        }

        log.info("RetryWorker: found {} eligible event(s) for retry", eligible.size());

        for (ProcessedEvent event : eligible) {
            try {
                retryExecutionService.executeRetry(event);
            } catch (Exception ex) {
                // Log and continue with next event; do not crash the worker
                log.error("RetryWorker: unexpected error processing event {}: {}",
                        event.getIdempotencyKey(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Fetches eligible events using SKIP LOCKED in its own short transaction.
     * Keeps the outer scheduling loop transaction-free.
     */
    @Transactional
    public List<ProcessedEvent> fetchEligibleBatch() {
        return processedEventRepository.findEligibleForRetryWithLock(workerProperties.getBatchSize());
    }
}
