-- Phase 4: Retry mechanism, failure recovery, and dead-letter support

ALTER TABLE processed_events ALTER COLUMN response_payload DROP NOT NULL;
ALTER TABLE processed_events ALTER COLUMN response_status_code DROP NOT NULL;

ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS request_payload TEXT;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS max_retries INT NOT NULL DEFAULT 3;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS last_failure_reason TEXT;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_processed_events_retry_queue 
ON processed_events (status, next_retry_at) 
WHERE status = 'RETRY_PENDING';
