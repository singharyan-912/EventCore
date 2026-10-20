CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    resource_id VARCHAR(100) NOT NULL UNIQUE,
    available_quantity INT NOT NULL CHECK (available_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE processed_events (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    request_payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL,
    response_payload TEXT NOT NULL,
    response_status_code INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processed_events_key ON processed_events (idempotency_key);
CREATE INDEX idx_inventory_resource_id ON inventory (resource_id);

-- Initialize demo inventory item (safe and repeatable across restarts)
INSERT INTO inventory (resource_id, available_quantity)
VALUES ('product-101', 10)
ON CONFLICT (resource_id) DO NOTHING;
