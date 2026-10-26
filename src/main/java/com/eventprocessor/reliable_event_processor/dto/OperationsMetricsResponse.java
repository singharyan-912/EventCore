package com.eventprocessor.reliable_event_processor.dto;

public record OperationsMetricsResponse(
        String inMemoryMetricsNotice,
        long attempts,
        long successes,
        long failures,
        long duplicates,
        long retries,
        long deadLetters,
        long outboxPublished,
        long outboxFailed,
        long consumerSuccess,
        long consumerFailure,
        long consumerDuplicate,
        EventSummaryResponse databaseEvents,
        OutboxSummaryResponse databaseOutbox
) {}
