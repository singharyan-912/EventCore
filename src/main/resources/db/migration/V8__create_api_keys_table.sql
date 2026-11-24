-- Phase B: Business Registration & API Credential Management
-- Create api_keys table for secure business credential management

CREATE TABLE IF NOT EXISTS api_keys (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_api_keys_prefix_status ON api_keys (key_prefix, status);
CREATE INDEX IF NOT EXISTS idx_api_keys_tenant_status ON api_keys (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_api_keys_tenant ON api_keys (tenant_id);
