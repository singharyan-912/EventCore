package com.eventprocessor.reliable_event_processor.health;

import com.eventprocessor.reliable_event_processor.config.WorkerProperties;
import com.eventprocessor.reliable_event_processor.repository.ProcessedEventRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the background Retry Worker.
 *
 * Verifies active database queue status rather than mere bean existence.
 */
@Component
public class RetryWorkerHealthIndicator implements HealthIndicator {

    private final WorkerProperties workerProperties;
    private final ProcessedEventRepository processedEventRepository;

    public RetryWorkerHealthIndicator(WorkerProperties workerProperties,
                                      ProcessedEventRepository processedEventRepository) {
        this.workerProperties = workerProperties;
        this.processedEventRepository = processedEventRepository;
    }

    @Override
    public Health health() {
        if (!workerProperties.isEnabled()) {
            return Health.unknown()
                    .withDetail("enabled", false)
                    .withDetail("status", "DISABLED")
                    .build();
        }

        try {
            long retryPending = processedEventRepository.countByStatus("RETRY_PENDING");
            long deadLetter = processedEventRepository.countByStatus("DEAD_LETTER");
            long processing = processedEventRepository.countByStatus("PROCESSING");

            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("status", "HEALTHY")
                    .withDetail("retryPendingCount", retryPending)
                    .withDetail("deadLetterCount", deadLetter)
                    .withDetail("processingCount", processing)
                    .withDetail("pollIntervalMs", workerProperties.getPollIntervalMs())
                    .withDetail("batchSize", workerProperties.getBatchSize())
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("enabled", true)
                    .withDetail("reason", "Failed to query retry queue from database")
                    .build();
        }
    }
}
