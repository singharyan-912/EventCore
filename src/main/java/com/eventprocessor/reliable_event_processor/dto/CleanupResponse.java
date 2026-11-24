package com.eventprocessor.reliable_event_processor.dto;

import java.time.OffsetDateTime;

public record CleanupResponse(
        boolean dryRun,
        int outboxRecordsDeleted,
        int webhookDeliveriesDeleted,
        int auditLogsDeleted,
        int eventsArchived,
        OffsetDateTime executedAt
) {}
