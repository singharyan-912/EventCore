-- Phase 7: User persistence and authentication schema

CREATE TABLE IF NOT EXISTS app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON app_users (username);
CREATE INDEX IF NOT EXISTS idx_users_email ON app_users (email);
CREATE INDEX IF NOT EXISTS idx_users_role ON app_users (role);
CREATE INDEX IF NOT EXISTS idx_users_status ON app_users (status);

