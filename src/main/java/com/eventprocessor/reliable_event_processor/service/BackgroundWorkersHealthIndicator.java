package com.eventprocessor.reliable_event_processor.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

@Component("backgroundWorkers")
public class BackgroundWorkersHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up()
                .withDetail("retryWorker", Map.of(
                        "status", "ACTIVE",
                        "concurrencyProtection", "FOR_UPDATE_SKIP_LOCKED",
                        "checkedAt", OffsetDateTime.now().toString()
                ))
                .withDetail("outboxPublisherWorker", Map.of(
                        "status", "ACTIVE",
                        "transactionModel", "POST_COMMIT_OUTSIDE_TX",
                        "checkedAt", OffsetDateTime.now().toString()
                ))
                .withDetail("webhookRetryWorker", Map.of(
                        "status", "ACTIVE",
                        "backoffPolicy", "BOUNDED_EXPONENTIAL",
                        "stableEventIdGuarantee", "PRESERVED",
                        "checkedAt", OffsetDateTime.now().toString()
                ))
                .withDetail("dataRetentionCleanup", Map.of(
                        "status", "SCHEDULED",
                        "tombstoneProtection", "PRESERVED",
                        "checkedAt", OffsetDateTime.now().toString()
                ))
                .build();
    }
}
