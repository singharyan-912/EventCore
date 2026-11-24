package com.eventprocessor.reliable_event_processor.entity;

public enum EventStatus {
    PROCESSING,
    RETRY_PENDING,
    PROCESSED,
    DEAD_LETTER,
    RECONCILIATION_REQUIRED,
    RECONCILIATION_IN_PROGRESS,
    RECONCILED_SUCCESS,
    RECONCILED_FAILURE
}
