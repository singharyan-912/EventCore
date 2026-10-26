-- Phase 5 - Part 1: Transactional Outbox Pattern for Kafka publishing

CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100),
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (status, id) WHERE status IN ('PENDING', 'CLAIMED');
CREATE INDEX idx_outbox_events_idempotency_key ON outbox_events (idempotency_key);
