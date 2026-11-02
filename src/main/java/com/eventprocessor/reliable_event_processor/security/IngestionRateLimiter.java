package com.eventprocessor.reliable_event_processor.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter for public event ingestion endpoint.
 * Protects against volumetric abuse while maintaining high throughput for legitimate event ingestion.
 *
 * NOTE FOR HORIZONTAL SCALING:
 * In multi-node deployments, an API gateway or distributed Redis token bucket should be used.
 */
@Component
public class IngestionRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(IngestionRateLimiter.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 1_000;
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_ENTRIES = 20_000;

    private static class Counter {
        final AtomicInteger count = new AtomicInteger(1);
        final long firstRequestTimestamp;

        Counter(long timestamp) {
            this.firstRequestTimestamp = timestamp;
        }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public boolean allowRequest(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return true;
        }

        if (counters.size() >= MAX_ENTRIES) {
            cleanupExpired();
        }

        long now = System.currentTimeMillis();
        Counter counter = counters.compute(clientIp, (k, existing) -> {
            if (existing == null || (now - existing.firstRequestTimestamp > WINDOW_MS)) {
                return new Counter(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return counter.count.get() <= MAX_REQUESTS_PER_MINUTE;
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(entry -> now - entry.getValue().firstRequestTimestamp > WINDOW_MS);
    }
}

