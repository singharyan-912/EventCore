-- Phase A: Multi-Tenant Schema and Isolation

-- 1. Create tenants table
CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL UNIQUE,
    business_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenants_tenant_id ON tenants (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants (status);

-- 2. Insert default tenant for backward compatibility and existing data migration
INSERT INTO tenants (tenant_id, business_name, status)
VALUES ('DEFAULT_BUSINESS', 'Default Business', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

-- 3. Add tenant_id to inventory, backfill, alter NOT NULL, drop global constraint, add composite constraint
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);
UPDATE inventory SET tenant_id = 'DEFAULT_BUSINESS' WHERE tenant_id IS NULL;
ALTER TABLE inventory ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE inventory ADD CONSTRAINT fk_inventory_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);
ALTER TABLE inventory DROP CONSTRAINT IF EXISTS inventory_resource_id_key;
ALTER TABLE inventory ADD CONSTRAINT uk_inventory_tenant_resource UNIQUE (tenant_id, resource_id);
CREATE INDEX IF NOT EXISTS idx_inventory_tenant_resource ON inventory (tenant_id, resource_id);

-- 4. Add tenant_id to processed_events, backfill, alter NOT NULL, drop global unique key, add composite unique
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);
UPDATE processed_events SET tenant_id = 'DEFAULT_BUSINESS' WHERE tenant_id IS NULL;
ALTER TABLE processed_events ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE processed_events ADD CONSTRAINT fk_processed_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);
ALTER TABLE processed_events DROP CONSTRAINT IF EXISTS processed_events_idempotency_key_key;
ALTER TABLE processed_events ADD CONSTRAINT uk_processed_events_tenant_key UNIQUE (tenant_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_processed_events_tenant_key ON processed_events (tenant_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_processed_events_tenant_status ON processed_events (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_processed_events_tenant_created ON processed_events (tenant_id, created_at DESC);

-- 5. Add tenant_id to outbox_events, backfill, alter NOT NULL, add foreign key & index
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);
UPDATE outbox_events SET tenant_id = 'DEFAULT_BUSINESS' WHERE tenant_id IS NULL;
ALTER TABLE outbox_events ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE outbox_events ADD CONSTRAINT fk_outbox_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);
CREATE INDEX IF NOT EXISTS idx_outbox_events_tenant ON outbox_events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_outbox_events_tenant_status ON outbox_events (tenant_id, status, id);

-- 6. Add tenant_id to operational_audit_logs, backfill, alter NOT NULL, add foreign key & index
ALTER TABLE operational_audit_logs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);
UPDATE operational_audit_logs SET tenant_id = 'DEFAULT_BUSINESS' WHERE tenant_id IS NULL;
ALTER TABLE operational_audit_logs ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE operational_audit_logs ADD CONSTRAINT fk_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON operational_audit_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_timestamp ON operational_audit_logs (tenant_id, timestamp DESC);

-- 7. Add tenant_id to app_users, backfill, alter NOT NULL, add foreign key & index
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);
UPDATE app_users SET tenant_id = 'DEFAULT_BUSINESS' WHERE tenant_id IS NULL;
ALTER TABLE app_users ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE app_users ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant ON app_users (tenant_id);
