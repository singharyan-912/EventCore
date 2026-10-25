package com.eventprocessor.reliable_event_processor.entity;

public enum EventStatus {
    PROCESSING,
    RETRY_PENDING,
    PROCESSED,
    DEAD_LETTER
}
