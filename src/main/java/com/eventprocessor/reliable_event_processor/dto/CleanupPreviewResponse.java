package com.eventprocessor.reliable_event_processor.dto;

public record CleanupPreviewResponse(
        int outboxRecordsEligible,
        int webhookDeliveriesEligible,
        int auditLogsEligible,
        int eventsEligibleForArchival
) {}
