package com.eventprocessor.reliable_event_processor.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter to protect against login brute-force and credential-stuffing attacks.
 *
 * NOTE FOR PRODUCTION DEPLOYMENTS:
 * This rate limiter maintains state in-memory per application instance. In horizontally scaled,
 * multi-instance production environments, an external distributed cache (such as Redis) or
 * an edge API gateway rate limiter should be used for globally shared rate limiting across nodes.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000L; // 1 minute
    private static final int MAX_ENTRIES = 10_000;  // Bounded memory protection

    private static class Attempt {
        final AtomicInteger count = new AtomicInteger(1);
        final long firstAttemptTimestamp;

        Attempt(long timestamp) {
            this.firstAttemptTimestamp = timestamp;
        }
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - attempt.firstAttemptTimestamp > WINDOW_MS) {
            attempts.remove(key);
            return false;
        }

        return attempt.count.get() >= MAX_FAILED_ATTEMPTS;
    }

    public void recordFailedAttempt(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        // Bounded capacity check
        if (attempts.size() >= MAX_ENTRIES) {
            cleanupExpired();
        }

        long now = System.currentTimeMillis();
        attempts.compute(key, (k, existing) -> {
            if (existing == null || (now - existing.firstAttemptTimestamp > WINDOW_MS)) {
                return new Attempt(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });
    }

    public void reset(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().firstAttemptTimestamp > WINDOW_MS);
    }
}

