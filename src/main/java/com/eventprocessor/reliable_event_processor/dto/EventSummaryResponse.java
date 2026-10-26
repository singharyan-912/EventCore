package com.eventprocessor.reliable_event_processor.dto;

import java.util.Map;

public record EventSummaryResponse(
        long totalEvents,
        long processedCount,
        long retryPendingCount,
        long deadLetterCount,
        long processingCount,
        Map<String, Long> countsByStatus
) {}
