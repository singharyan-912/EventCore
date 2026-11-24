package com.eventprocessor.reliable_event_processor.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter to protect public business registration against abuse and automated spam.
 */
@Component
public class RegistrationRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RegistrationRateLimiter.class);

    private static final int MAX_REGISTRATIONS_PER_HOUR = 10;
    private static final long WINDOW_MS = 3600_000L; // 1 hour
    private static final int MAX_ENTRIES = 5_000;

    private static class Counter {
        final AtomicInteger count = new AtomicInteger(1);
        final long firstRequestTimestamp;

        Counter(long timestamp) {
            this.firstRequestTimestamp = timestamp;
        }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public boolean allowRegistration(String clientIp) {
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

        boolean allowed = counter.count.get() <= MAX_REGISTRATIONS_PER_HOUR;
        if (!allowed) {
            log.warn("Registration rate limit exceeded for client IP: {}", clientIp);
        }
        return allowed;
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(entry -> now - entry.getValue().firstRequestTimestamp > WINDOW_MS);
    }

    public void clear() {
        counters.clear();
    }
}
