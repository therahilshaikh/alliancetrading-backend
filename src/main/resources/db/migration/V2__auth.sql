-- V2: OTP requests, sessions (single-active enforcement via partial unique indexes)

CREATE TABLE otp_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile      VARCHAR(10)  NOT NULL,
    otp_hash    VARCHAR(64)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_requests_mobile_created ON otp_requests(mobile, created_at DESC);

CREATE TABLE sessions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID REFERENCES users(id),
    admin_user_id               UUID REFERENCES admin_users(id),
    session_type                VARCHAR(10) NOT NULL CHECK (session_type IN ('USER','ADMIN')),
    device_id                   VARCHAR(200),
    device_info                 VARCHAR(500),
    refresh_token_hash          VARCHAR(64) NOT NULL,
    prev_refresh_token_hash     VARCHAR(64),
    refresh_token_expires_at    TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at                  TIMESTAMPTZ,
    revoke_reason               VARCHAR(50),
    CHECK (
        (session_type = 'USER'  AND user_id IS NOT NULL AND admin_user_id IS NULL) OR
        (session_type = 'ADMIN' AND admin_user_id IS NOT NULL AND user_id IS NULL)
    )
);

-- Single active session per user
CREATE UNIQUE INDEX idx_sessions_user_active
    ON sessions(user_id)
    WHERE revoked_at IS NULL AND session_type = 'USER';

-- Admins may be signed in on several devices at once (no uniqueness); index for lookups
CREATE INDEX idx_sessions_admin_active
    ON sessions(admin_user_id)
    WHERE revoked_at IS NULL AND session_type = 'ADMIN';

CREATE INDEX idx_sessions_refresh_hash ON sessions(refresh_token_hash);
CREATE INDEX idx_sessions_prev_refresh_hash ON sessions(prev_refresh_token_hash) WHERE prev_refresh_token_hash IS NOT NULL;
