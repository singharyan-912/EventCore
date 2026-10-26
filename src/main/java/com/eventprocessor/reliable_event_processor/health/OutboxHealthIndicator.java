package com.eventprocessor.reliable_event_processor.health;

import com.eventprocessor.reliable_event_processor.config.OutboxProperties;
import com.eventprocessor.reliable_event_processor.repository.OutboxEventRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the Transactional Outbox Publisher.
 *
 * Reports actual database outbox queue counts rather than mere bean existence.
 */
@Component
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxProperties outboxProperties;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxHealthIndicator(OutboxProperties outboxProperties,
                                 OutboxEventRepository outboxEventRepository) {
        this.outboxProperties = outboxProperties;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public Health health() {
        if (!outboxProperties.isEnabled()) {
            return Health.unknown()
                    .withDetail("enabled", false)
                    .withDetail("status", "DISABLED")
                    .build();
        }

        try {
            long pending = outboxEventRepository.countByStatus("PENDING");
            long claimed = outboxEventRepository.countByStatus("CLAIMED");
            long failed = outboxEventRepository.countByStatus("FAILED");
            long published = outboxEventRepository.countByStatus("PUBLISHED");

            String state = failed > 0 ? "DEGRADED_PUBLISH_FAILURES" : "HEALTHY";

            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("status", state)
                    .withDetail("topic", outboxProperties.getTopic())
                    .withDetail("pendingCount", pending)
                    .withDetail("claimedCount", claimed)
                    .withDetail("failedCount", failed)
                    .withDetail("publishedCount", published)
                    .withDetail("pollIntervalMs", outboxProperties.getPollIntervalMs())
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("enabled", true)
                    .withDetail("reason", "Failed to query outbox queue from database")
                    .build();
        }
    }
}
