package com.eventprocessor.reliable_event_processor.entity;

public enum OutboxStatus {
    PENDING,
    CLAIMED,
    PUBLISHED,
    FAILED
}
