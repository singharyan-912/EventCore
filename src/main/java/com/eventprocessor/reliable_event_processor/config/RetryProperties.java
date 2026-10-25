package com.eventprocessor.reliable_event_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "event-processor.retry")
public class RetryProperties {

    private int maxAttempts = 3;
    private long initialDelayMs = 2000;
    private double multiplier = 2.0;
    private long maxDelayMs = 60000;

    public Duration calculateBackoff(int attempt) {
        double delay = initialDelayMs * Math.pow(multiplier, Math.max(0, attempt - 1));
        long finalDelay = (long) Math.min(delay, maxDelayMs);
        return Duration.ofMillis(finalDelay);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
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

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }
}
