-- Phase C: Generic Operation Engine
-- Create appointments table for APPOINTMENT_CREATE demonstration operation

CREATE TABLE IF NOT EXISTS appointments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    appointment_id VARCHAR(100) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    appointment_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_appointments_tenant_id UNIQUE (tenant_id, appointment_id)
);

CREATE INDEX IF NOT EXISTS idx_appointments_tenant_customer ON appointments (tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_appointments_tenant_status ON appointments (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_appointments_tenant ON appointments (tenant_id);
