package com.eventprocessor.reliable_event_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "event-processor")
public class ReliabilityEnhancementProperties {

    private Retention retention = new Retention();
    private Lease lease = new Lease();
    private Webhook webhook = new Webhook();
    private Cleanup cleanup = new Cleanup();

    public Retention getRetention() {
        return retention;
    }

    public void setRetention(Retention retention) {
        this.retention = retention;
    }

    public Lease getLease() {
        return lease;
    }

    public void setLease(Lease lease) {
        this.lease = lease;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public void setCleanup(Cleanup cleanup) {
        this.cleanup = cleanup;
    }

    public static class Retention {
        private int idempotencyDays = 30;

        public int getIdempotencyDays() {
            return idempotencyDays;
        }

        public void setIdempotencyDays(int idempotencyDays) {
            this.idempotencyDays = idempotencyDays;
        }
    }

    public static class Lease {
        private int processingLeaseSeconds = 300; // 5 minutes

        public int getProcessingLeaseSeconds() {
            return processingLeaseSeconds;
        }

        public void setProcessingLeaseSeconds(int processingLeaseSeconds) {
            this.processingLeaseSeconds = processingLeaseSeconds;
        }
    }

    public static class Webhook {
        private boolean enabled = true;
        private int maxRetries = 3;
        private long initialDelayMs = 2000;
        private double multiplier = 2.0;
        private long pollIntervalMs = 2000;
        private int batchSize = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration calculateBackoff(int attempt) {
            long delayMs = (long) (initialDelayMs * Math.pow(multiplier, Math.max(0, attempt - 1)));
            return Duration.ofMillis(delayMs);
        }
    }

    public static class Cleanup {
        private boolean enabled = true;
        private int outboxRetentionDays = 7;
        private int webhookDeliveryRetentionDays = 14;
        private int auditRetentionDays = 90;
        private int archivedEventsRetentionDays = 30;
        private int batchSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getOutboxRetentionDays() {
            return outboxRetentionDays;
        }

        public void setOutboxRetentionDays(int outboxRetentionDays) {
            this.outboxRetentionDays = outboxRetentionDays;
        }

        public int getWebhookDeliveryRetentionDays() {
            return webhookDeliveryRetentionDays;
        }

        public void setWebhookDeliveryRetentionDays(int webhookDeliveryRetentionDays) {
            this.webhookDeliveryRetentionDays = webhookDeliveryRetentionDays;
        }

        public int getAuditRetentionDays() {
            return auditRetentionDays;
        }

        public void setAuditRetentionDays(int auditRetentionDays) {
            this.auditRetentionDays = auditRetentionDays;
        }

        public int getArchivedEventsRetentionDays() {
            return archivedEventsRetentionDays;
        }

        public void setArchivedEventsRetentionDays(int archivedEventsRetentionDays) {
            this.archivedEventsRetentionDays = archivedEventsRetentionDays;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
