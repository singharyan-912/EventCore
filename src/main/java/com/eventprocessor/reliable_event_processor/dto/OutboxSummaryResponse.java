package com.eventprocessor.reliable_event_processor.dto;

import java.util.Map;

public record OutboxSummaryResponse(
        long totalOutboxEvents,
        long pendingCount,
        long claimedCount,
        long publishedCount,
        long failedCount,
        long retriedCount,
        Map<String, Long> countsByStatus
) {}
