-- V5: investments, ledger_entries (append-only), user_balances

-- No UTR is captured; the amount comes from the user's payment assignment.
-- payment_setting_id is the older global-setting source, still mapped by the entity, so either source must be present.
CREATE TABLE investments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id),
    amount                NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    remaining_principal   NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (remaining_principal >= 0),
    payment_setting_id    UUID REFERENCES payment_settings(id),
    proof_document_id     UUID NOT NULL REFERENCES documents(id),
    status                VARCHAR(25) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                          CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','REJECTED','CLOSED')),
    rejection_reason      VARCHAR(500),
    approved_by           UUID REFERENCES admin_users(id),
    approved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    payment_assignment_id UUID REFERENCES user_payment_assignments(id),
    CONSTRAINT investments_payment_source_present
        CHECK (payment_assignment_id IS NOT NULL OR payment_setting_id IS NOT NULL)
);

-- A user holds many ACTIVE investments (top-ups), but only one proof may be under review at a time
CREATE UNIQUE INDEX idx_investments_one_pending
    ON investments(user_id)
    WHERE status = 'PENDING_VERIFICATION';

CREATE INDEX idx_investments_status ON investments(status);

CREATE TABLE ledger_entries (
    id               BIGSERIAL PRIMARY KEY,
    journal_id       UUID NOT NULL,
    user_id          UUID NOT NULL REFERENCES users(id),
    investment_id    UUID REFERENCES investments(id),
    bucket           VARCHAR(10) NOT NULL CHECK (bucket IN ('PRINCIPAL','PROFIT','HELD')),
    entry_type       VARCHAR(25) NOT NULL CHECK (entry_type IN (
                         'DEPOSIT','PNL','PNL_ADJUSTMENT',
                         'WITHDRAWAL_HOLD','WITHDRAWAL_RELEASE',
                         'WITHDRAWAL_PAYOUT','WITHDRAWAL_FEE')),
    amount           NUMERIC(15,2) NOT NULL,
    source_type      VARCHAR(50) NOT NULL,
    source_id        VARCHAR(64) NOT NULL,
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    created_by_type  VARCHAR(10) NOT NULL CHECK (created_by_type IN ('USER','ADMIN','SYSTEM')),
    created_by_id    UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_user ON ledger_entries(user_id, id);
CREATE INDEX idx_ledger_investment ON ledger_entries(investment_id);
CREATE INDEX idx_ledger_journal ON ledger_entries(journal_id);

-- Append-only enforcement (reject_mutation function defined in V3)
CREATE TRIGGER ledger_entries_no_update BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
CREATE TRIGGER ledger_entries_no_delete BEFORE DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TABLE user_balances (
    user_id    UUID PRIMARY KEY REFERENCES users(id),
    principal  NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (principal >= 0),
    profit     NUMERIC(15,2) NOT NULL DEFAULT 0,
    held       NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (held >= 0),
    version    BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
