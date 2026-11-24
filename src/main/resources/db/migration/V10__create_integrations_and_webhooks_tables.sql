-- Phase D: External Application Integration
-- Tables for external integrations, webhooks, and webhook deliveries

CREATE TABLE IF NOT EXISTS external_integrations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    name VARCHAR(100) NOT NULL,
    integration_type VARCHAR(50) NOT NULL DEFAULT 'HTTP_REST',
    base_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    supported_operation_types VARCHAR(255) NOT NULL,
    auth_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    auth_header_name VARCHAR(100),
    encrypted_secret TEXT,
    secret_prefix VARCHAR(10),
    timeout_ms INT NOT NULL DEFAULT 5000,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_connection_at TIMESTAMP WITH TIME ZONE,
    last_connection_status VARCHAR(50),
    last_connection_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_integrations_tenant_status ON external_integrations (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_integrations_tenant ON external_integrations (tenant_id);

CREATE TABLE IF NOT EXISTS webhooks (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    name VARCHAR(100) NOT NULL,
    destination_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    subscribed_events VARCHAR(255) NOT NULL,
    encrypted_signing_secret TEXT NOT NULL,
    secret_prefix VARCHAR(10),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_delivery_at TIMESTAMP WITH TIME ZONE,
    last_delivery_status VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_webhooks_tenant_status ON webhooks (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_webhooks_tenant ON webhooks (tenant_id);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    webhook_id BIGINT NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    http_status INT,
    attempt_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_tenant_event ON webhook_deliveries (tenant_id, event_id);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_webhook ON webhook_deliveries (webhook_id);
