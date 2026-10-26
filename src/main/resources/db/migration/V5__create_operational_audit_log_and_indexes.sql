-- Phase 6: Operational Audit Logs and Performance Indexes

CREATE TABLE IF NOT EXISTS operational_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    action_type VARCHAR(100) NOT NULL,
    target_identifier VARCHAR(255) NOT NULL,
    outcome VARCHAR(50) NOT NULL,
    failure_reason TEXT,
    principal VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    details TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON operational_audit_logs (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target ON operational_audit_logs (target_identifier);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON operational_audit_logs (action_type);

-- Query performance indexes for event inspection and filtering
CREATE INDEX IF NOT EXISTS idx_processed_events_created_at ON processed_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_processed_events_status_created ON processed_events (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_processed_events_resource_id ON processed_events (resource_id);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON processed_events (event_type);
