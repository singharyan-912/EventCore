package com.eventprocessor.reliable_event_processor.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Centralized operational metrics service using Micrometer.
 *
 * METRIC BOUNDING RULES:
 * - Never use idempotency keys, resource IDs, raw exception messages, or unbounded values as tags.
 * - All tag keys and values are strictly bounded to predictable categories (event_type, status, failure_type, topic).
 * - Duplicate / idempotent replays are recorded separately and DO NOT increment success counters.
 * - In-memory metrics reflect the lifespan of the running JVM and reset on application restart.
 */
@Service
public class EventMetricsService {

    private final MeterRegistry meterRegistry;

    public EventMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordEventAttempt(String eventType) {
        String boundedEventType = sanitizeTag(eventType, "UNKNOWN");
        Counter.builder("events.processing.attempts")
                .tag("event_type", boundedEventType)
                .description("Total number of initial event processing attempts")
                .register(meterRegistry)
                .increment();
    }

    public void recordEventSuccess(String eventType, Duration duration) {
        String boundedEventType = sanitizeTag(eventType, "UNKNOWN");
        Counter.builder("events.processing.success")
                .tag("event_type", boundedEventType)
                .description("Number of events successfully processed and committed")
                .register(meterRegistry)
                .increment();

        if (duration != null) {
            Timer.builder("events.processing.duration")
                    .tag("event_type", boundedEventType)
                    .tag("outcome", "SUCCESS")
                    .description("Duration of event business processing")
                    .register(meterRegistry)
                    .record(duration.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    public void recordEventFailure(String eventType, String failureType, Duration duration) {
        String boundedEventType = sanitizeTag(eventType, "UNKNOWN");
        String boundedFailureType = sanitizeTag(failureType, "GENERAL_FAILURE");

        Counter.builder("events.processing.failure")
                .tag("event_type", boundedEventType)
                .tag("failure_type", boundedFailureType)
                .description("Number of event processing failures by category")
                .register(meterRegistry)
                .increment();

        if (duration != null) {
            Timer.builder("events.processing.duration")
                    .tag("event_type", boundedEventType)
                    .tag("outcome", "FAILURE")
                    .description("Duration of event business processing")
                    .register(meterRegistry)
                    .record(duration.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    public void recordEventDuplicate(String eventType, String status) {
        String boundedEventType = sanitizeTag(eventType, "UNKNOWN");
        String boundedStatus = sanitizeTag(status, "UNKNOWN");

        Counter.builder("events.processed.duplicate")
                .tag("event_type", boundedEventType)
                .tag("status", boundedStatus)
                .description("Number of duplicate or idempotent replay requests detected")
                .register(meterRegistry)
                .increment();
    }

    public void recordRetryAttempt(String eventType) {
        String boundedEventType = sanitizeTag(eventType, "UNKNOWN");
        Counter.builder("events.retry.attempts")
                .tag("event_type", boundedEventType)
                .description("Total number of retry attempts executed by RetryWorker")
                .register(meterRegistry)
                .increment();
    }

    public void recordDeadLetter(String eventType, String reasonCategory) {
        String boundedEventType = sanitizeTag(eventType, "UNKNOWN");
        String boundedCategory = sanitizeTag(reasonCategory, "UNSPECIFIED");

        Counter.builder("events.deadletter.total")
                .tag("event_type", boundedEventType)
                .tag("reason_category", boundedCategory)
                .description("Number of events transitioned to DEAD_LETTER state")
                .register(meterRegistry)
                .increment();
    }

    public void recordOutboxSuccess(String topic) {
        String boundedTopic = sanitizeTag(topic, "UNKNOWN");
        Counter.builder("outbox.publication.success")
                .tag("topic", boundedTopic)
                .description("Count of successful transactional outbox event publications to Kafka")
                .register(meterRegistry)
                .increment();
    }

    public void recordOutboxFailure(String topic) {
        String boundedTopic = sanitizeTag(topic, "UNKNOWN");
        Counter.builder("outbox.publication.failure")
                .tag("topic", boundedTopic)
                .description("Count of failed transactional outbox publications to Kafka")
                .register(meterRegistry)
                .increment();
    }

    public void recordConsumerSuccess(String topic) {
        String boundedTopic = sanitizeTag(topic, "UNKNOWN");
        Counter.builder("consumer.processing.success")
                .tag("topic", boundedTopic)
                .description("Count of messages successfully ingested and processed by Kafka consumer")
                .register(meterRegistry)
                .increment();
    }

    public void recordConsumerFailure(String topic, String failureType) {
        String boundedTopic = sanitizeTag(topic, "UNKNOWN");
        String boundedFailureType = sanitizeTag(failureType, "GENERAL_FAILURE");

        Counter.builder("consumer.processing.failure")
                .tag("topic", boundedTopic)
                .tag("failure_type", boundedFailureType)
                .description("Count of Kafka consumer processing failures or DLT forwardings")
                .register(meterRegistry)
                .increment();
    }

    public void recordConsumerDuplicate(String topic) {
        String boundedTopic = sanitizeTag(topic, "UNKNOWN");
        Counter.builder("consumer.duplicate.deliveries")
                .tag("topic", boundedTopic)
                .description("Count of duplicate Kafka deliveries acknowledged without reprocessing")
                .register(meterRegistry)
                .increment();
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    private String sanitizeTag(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        // Normalize to upper alphanumeric and underscores with a strict length limit
        String clean = input.trim().toUpperCase().replaceAll("[^A-Z0-9_.-]", "_");
        if (clean.length() > 50) {
            clean = clean.substring(0, 50);
        }
        return clean.isEmpty() ? fallback : clean;
    }
}
