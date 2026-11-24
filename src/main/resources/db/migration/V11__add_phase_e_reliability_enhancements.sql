-- Phase E: Reliability Enhancements
-- 1. processed_events: lease tracking for crash recovery, reconciliation metadata, and archival / expiration
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS processing_lease_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS reconciled_by VARCHAR(100);
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS reconciliation_notes TEXT;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_processed_events_reconciliation
ON processed_events (tenant_id, status)
WHERE status IN ('RECONCILIATION_REQUIRED', 'RECONCILIATION_IN_PROGRESS');

CREATE INDEX IF NOT EXISTS idx_processed_events_stuck_lease
ON processed_events (status, processing_lease_expires_at)
WHERE status = 'PROCESSING';

CREATE INDEX IF NOT EXISTS idx_processed_events_expires
ON processed_events (tenant_id, expires_at)
WHERE expires_at IS NOT NULL;

-- 2. webhook_deliveries: retry scheduling and limits
ALTER TABLE webhook_deliveries ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE webhook_deliveries ADD COLUMN IF NOT EXISTS max_retries INT NOT NULL DEFAULT 3;

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_retry
ON webhook_deliveries (status, next_retry_at)
WHERE status = 'RETRY_PENDING';
