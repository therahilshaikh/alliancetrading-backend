-- V6: pnl_batches, pnl_lines, pnl_allocations, outbox

-- The maker picks which admin must approve the batch (assigned_checker_id).
-- Evidence uploads are optional while document uploads are disabled.
CREATE TABLE pnl_batches (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_type           VARCHAR(12) NOT NULL CHECK (batch_type IN ('REGULAR','ADJUSTMENT')),
    session_date         DATE NOT NULL,
    original_batch_id    UUID REFERENCES pnl_batches(id),
    control_total        NUMERIC(15,2) NOT NULL,
    evidence_document_id UUID REFERENCES documents(id),
    status               VARCHAR(12) NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT','APPROVED','REJECTED')),
    maker_id             UUID NOT NULL REFERENCES admin_users(id),
    checker_id           UUID REFERENCES admin_users(id),
    notes                VARCHAR(1000),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at          TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,
    assigned_checker_id  UUID REFERENCES admin_users(id),
    -- Every line applies to each target investment individually:
    -- FIXED = the same rupee amount each, PERCENTAGE = a percent of each investment's principal
    value_type           VARCHAR(12) NOT NULL CHECK (value_type IN ('FIXED','PERCENTAGE')),
    CHECK (checker_id IS NULL OR checker_id <> maker_id),
    CHECK (batch_type <> 'ADJUSTMENT' OR original_batch_id IS NOT NULL),
    -- The assigned approver can never be the maker
    CONSTRAINT pnl_batches_assigned_checker_not_maker
        CHECK (assigned_checker_id IS NULL OR assigned_checker_id <> maker_id),
    -- When an approver was assigned, only that admin may decide the batch
    CONSTRAINT pnl_batches_decided_by_assignee
        CHECK (checker_id IS NULL OR assigned_checker_id IS NULL OR checker_id = assigned_checker_id)
);

CREATE INDEX idx_pnl_batches_assigned_pending ON pnl_batches(assigned_checker_id)
    WHERE status = 'DRAFT';

CREATE TABLE pnl_lines (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id      UUID NOT NULL REFERENCES pnl_batches(id),
    target_type   VARCHAR(12) NOT NULL CHECK (target_type IN ('INVESTMENT','ALL')),
    investment_id UUID REFERENCES investments(id),
    -- Line total, computed from the targets at creation (re-checked on approval)
    amount        NUMERIC(15,2) NOT NULL,
    -- PERCENTAGE batches: percent of each target's principal (1.2500 = 1.25%)
    percentage    NUMERIC(7,4) CHECK (percentage IS NULL OR (percentage > -100 AND percentage <= 100 AND percentage <> 0)),
    -- FIXED batches: rupees each target investment receives
    amount_per_investment NUMERIC(15,2) CHECK (amount_per_investment IS NULL OR amount_per_investment <> 0),
    -- Exactly one way of valuing the line
    CHECK ((percentage IS NULL) <> (amount_per_investment IS NULL)),
    CHECK ((target_type = 'INVESTMENT') = (investment_id IS NOT NULL))
);

CREATE INDEX idx_pnl_lines_batch ON pnl_lines(batch_id);

CREATE TABLE pnl_allocations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id      UUID NOT NULL REFERENCES pnl_batches(id),
    batch_type    VARCHAR(12) NOT NULL CHECK (batch_type IN ('REGULAR','ADJUSTMENT')),
    investment_id UUID NOT NULL REFERENCES investments(id),
    user_id       UUID NOT NULL REFERENCES users(id),
    session_date  DATE NOT NULL,
    amount        NUMERIC(15,2) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pnl_allocations_batch ON pnl_allocations(batch_id);
CREATE INDEX idx_pnl_allocations_investment ON pnl_allocations(investment_id);

-- An investment receives REGULAR P&L at most once per session_date
CREATE UNIQUE INDEX idx_pnl_allocations_regular_once_per_day
    ON pnl_allocations(investment_id, session_date)
    WHERE batch_type = 'REGULAR';

-- Transactional outbox
CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    recipient_type VARCHAR(10) NOT NULL CHECK (recipient_type IN ('USER','ADMIN')),
    recipient_id   UUID NOT NULL,
    channel        VARCHAR(10) NOT NULL CHECK (channel IN ('SMS','PUSH')),
    payload        JSONB NOT NULL,
    status         VARCHAR(12) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts       INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_pending ON outbox(next_attempt_at) WHERE status = 'PENDING';
