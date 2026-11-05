-- V1: extensions, users, admin_users, idempotency_keys

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile                VARCHAR(10) NOT NULL UNIQUE,
    status                VARCHAR(20) NOT NULL DEFAULT 'REGISTERED'
                          CHECK (status IN ('REGISTERED','KYC_SUBMITTED','APPROVED','REJECTED','SUSPENDED')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    -- Admin suspension: the prior status is kept so reactivation restores it
    suspended_from_status VARCHAR(20),
    suspended_at          TIMESTAMPTZ,
    suspension_reason     VARCHAR(500),
    CONSTRAINT users_suspension_consistent
        CHECK ((status = 'SUSPENDED') = (suspended_from_status IS NOT NULL))
);

CREATE INDEX idx_users_status_created ON users(status, created_at DESC);

CREATE TABLE admin_users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               CITEXT NOT NULL UNIQUE,
    -- Human-readable name shown in the console (menu, greeting, maker/approver labels)
    display_name        VARCHAR(100) NOT NULL CHECK (length(btrim(display_name)) > 0),
    password_hash       VARCHAR(100) NOT NULL,
    role                VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','SUPER_ADMIN')),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    failed_login_count  INT NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE idempotency_keys (
    scope            VARCHAR(100) NOT NULL,
    key              VARCHAR(100) NOT NULL,
    actor_id         UUID,
    request_hash     VARCHAR(64) NOT NULL,
    response_status  INT,
    response_body    JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, key)
);
