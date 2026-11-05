-- V7: withdrawal_quotes, withdrawal_requests

CREATE TABLE withdrawal_quotes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    withdrawal_type VARCHAR(10) NOT NULL CHECK (withdrawal_type IN ('PROFIT','PRINCIPAL')),
    gross_amount    NUMERIC(15,2) NOT NULL CHECK (gross_amount > 0),
    fee_setting_id  UUID NOT NULL REFERENCES fee_settings(id),
    fee_pct         NUMERIC(6,4) NOT NULL,
    fee_amount      NUMERIC(15,2) NOT NULL,
    net_amount      NUMERIC(15,2) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (net_amount = gross_amount - fee_amount)
);

CREATE INDEX idx_withdrawal_quotes_user ON withdrawal_quotes(user_id);

CREATE TABLE withdrawal_requests (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users(id),
    investment_id            UUID NOT NULL REFERENCES investments(id),
    withdrawal_type          VARCHAR(10) NOT NULL CHECK (withdrawal_type IN ('PROFIT','PRINCIPAL')),
    gross_amount             NUMERIC(15,2) NOT NULL CHECK (gross_amount > 0),
    fee_setting_id           UUID NOT NULL REFERENCES fee_settings(id),
    fee_pct                  NUMERIC(6,4) NOT NULL,
    fee_amount               NUMERIC(15,2) NOT NULL,
    net_amount               NUMERIC(15,2) NOT NULL,
    bank_account_id          UUID NOT NULL REFERENCES bank_accounts(id),
    bank_holder_name         VARCHAR(200) NOT NULL,
    bank_account_last4       CHAR(4) NOT NULL,
    bank_ifsc                VARCHAR(11) NOT NULL,
    bank_vpa                 VARCHAR(100),
    status                   VARCHAR(20) NOT NULL DEFAULT 'REQUESTED'
                             CHECK (status IN ('REQUESTED','APPROVED','CANCELLED','REJECTED','PAID','PAYOUT_FAILED')),
    idempotency_key          VARCHAR(100) NOT NULL UNIQUE,
    exit_principal           NUMERIC(15,2),
    exit_profit              NUMERIC(15,2),
    approved_by              UUID REFERENCES admin_users(id),
    second_approved_by       UUID REFERENCES admin_users(id),
    paid_by                  UUID REFERENCES admin_users(id),
    payout_utr               VARCHAR(22),
    payout_proof_document_id UUID REFERENCES documents(id),
    paid_at                  TIMESTAMPTZ,
    rejection_reason         VARCHAR(500),
    failure_reason           VARCHAR(500),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CHECK (net_amount = gross_amount - fee_amount),
    CHECK (second_approved_by IS NULL OR approved_by IS NULL OR second_approved_by <> approved_by)
);

-- ONE open withdrawal per user
CREATE UNIQUE INDEX idx_withdrawals_one_open
    ON withdrawal_requests(user_id)
    WHERE status IN ('REQUESTED','APPROVED','PAYOUT_FAILED');

CREATE INDEX idx_withdrawals_status ON withdrawal_requests(status);
CREATE INDEX idx_withdrawals_user ON withdrawal_requests(user_id, created_at DESC);
