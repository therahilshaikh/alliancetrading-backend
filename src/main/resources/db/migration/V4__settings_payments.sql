-- V4: versioned settings (a SUPER_ADMIN save is final; the latest row with effective_from <= now is in force),
--     company payment accounts, per-user payment assignments

CREATE TABLE fee_settings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_type VARCHAR(10) NOT NULL CHECK (withdrawal_type IN ('PROFIT','PRINCIPAL')),
    fee_pct         NUMERIC(6,4) NOT NULL CHECK (fee_pct >= 0 AND fee_pct < 1),
    effective_from  TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL REFERENCES admin_users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fee_settings_current
    ON fee_settings(withdrawal_type, effective_from DESC);

CREATE TABLE app_settings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key             VARCHAR(100) NOT NULL,
    value           VARCHAR(500) NOT NULL,
    effective_from  TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL REFERENCES admin_users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_app_settings_current
    ON app_settings(key, effective_from DESC);

-- Saved company UPI accounts; any admin can add one, effective immediately
CREATE TABLE payment_accounts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label          VARCHAR(100) NOT NULL,
    vpa            VARCHAR(100) NOT NULL,
    payee_name     VARCHAR(100) NOT NULL,
    qr_document_id UUID REFERENCES documents(id),
    status         VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_by     UUID NOT NULL REFERENCES admin_users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_by    UUID REFERENCES admin_users(id),
    archived_at    TIMESTAMPTZ,
    version        BIGINT NOT NULL DEFAULT 0
);

-- The same UPI ID cannot be saved twice while active
CREATE UNIQUE INDEX idx_payment_accounts_active_vpa
    ON payment_accounts(lower(vpa)) WHERE status = 'ACTIVE';

-- Default payment: amount + the saved account members pay into (its UPI ID and QR are read from the account)
CREATE TABLE payment_settings (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fixed_amount       NUMERIC(15,2) NOT NULL CHECK (fixed_amount > 0),
    payment_account_id UUID NOT NULL REFERENCES payment_accounts(id),
    effective_from     TIMESTAMPTZ NOT NULL,
    created_by         UUID NOT NULL REFERENCES admin_users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_payment_settings_current
    ON payment_settings(effective_from DESC);
CREATE INDEX idx_payment_settings_account ON payment_settings(payment_account_id);

-- Each approved user is assigned the UPI account + amount they must pay
CREATE TABLE user_payment_assignments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id),
    payment_account_id UUID NOT NULL REFERENCES payment_accounts(id),
    amount             NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_by        UUID NOT NULL REFERENCES admin_users(id),
    assigned_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deactivated_at     TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0
);

-- One active assignment per user; history is kept as inactive rows
CREATE UNIQUE INDEX idx_user_payment_assignments_one_active
    ON user_payment_assignments(user_id) WHERE active;
CREATE INDEX idx_user_payment_assignments_user ON user_payment_assignments(user_id, assigned_at DESC);
