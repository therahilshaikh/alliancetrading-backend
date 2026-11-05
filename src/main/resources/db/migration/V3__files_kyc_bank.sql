-- V3: documents, kyc_profiles, bank_accounts, audit_log

CREATE TABLE documents (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id    UUID REFERENCES users(id),
    doc_type         VARCHAR(30) NOT NULL CHECK (doc_type IN (
                         'PAN_CARD','AADHAAR_MASKED_FRONT','AADHAAR_MASKED_BACK',
                         'BANK_PROOF','PAYMENT_PROOF','PAYOUT_PROOF',
                         'PNL_EVIDENCE','PAYMENT_QR')),
    bucket           VARCHAR(100) NOT NULL,
    object_key       VARCHAR(200) NOT NULL,
    -- Cleaned upload name, returned as the download filename (never part of the object key)
    original_filename VARCHAR(255),
    mime             VARCHAR(50) NOT NULL,
    size_bytes       BIGINT NOT NULL,
    sha256           VARCHAR(64) NOT NULL,
    wrapped_key      TEXT NOT NULL,
    key_id           VARCHAR(20) NOT NULL,
    uploaded_by_type VARCHAR(10) NOT NULL CHECK (uploaded_by_type IN ('USER','ADMIN')),
    uploaded_by_id   UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_owner ON documents(owner_user_id);

-- KYC collects full PAN + full Aadhaar (encrypted, with a lookup hash); no address.
-- Document scans are optional while uploads are disabled.
CREATE TABLE kyc_profiles (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID NOT NULL UNIQUE REFERENCES users(id),
    full_name                 VARCHAR(200) NOT NULL,
    dob                       DATE NOT NULL,
    pan_enc                   TEXT NOT NULL,
    pan_hash                  VARCHAR(64) NOT NULL UNIQUE,
    pan_last4                 CHAR(4) NOT NULL,
    aadhaar_last4             CHAR(4) NOT NULL,
    pan_card_document_id      UUID REFERENCES documents(id),
    aadhaar_front_document_id UUID REFERENCES documents(id),
    aadhaar_back_document_id  UUID REFERENCES documents(id),
    reviewed_by               UUID REFERENCES admin_users(id),
    reviewed_at               TIMESTAMPTZ,
    rejection_reason          VARCHAR(500),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                   BIGINT NOT NULL DEFAULT 0,
    aadhaar_enc               TEXT,
    aadhaar_hash              VARCHAR(64)
);

CREATE UNIQUE INDEX idx_kyc_profiles_aadhaar_hash ON kyc_profiles(aadhaar_hash)
    WHERE aadhaar_hash IS NOT NULL;
CREATE INDEX idx_kyc_profiles_created ON kyc_profiles(created_at);

CREATE TABLE bank_accounts (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                       UUID NOT NULL REFERENCES users(id),
    holder_name                   VARCHAR(200) NOT NULL,
    account_number_enc            TEXT NOT NULL,
    account_number_hash           VARCHAR(64) NOT NULL,
    account_number_last4          CHAR(4) NOT NULL,
    ifsc                          VARCHAR(11) NOT NULL,
    vpa                           VARCHAR(100),
    proof_document_id             UUID REFERENCES documents(id),
    status                        VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                                  CHECK (status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','REPLACED')),
    cooloff_until                 TIMESTAMPTZ,
    rejection_reason              VARCHAR(500),
    reviewed_by                   UUID REFERENCES admin_users(id),
    reviewed_at                   TIMESTAMPTZ,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                       BIGINT NOT NULL DEFAULT 0,
    account_type                  VARCHAR(10) NOT NULL DEFAULT 'SAVINGS'
                                  CHECK (account_type IN ('SAVINGS','CURRENT','SALARY'))
);

CREATE INDEX idx_bank_accounts_user ON bank_accounts(user_id);
CREATE INDEX idx_bank_accounts_status ON bank_accounts(status) WHERE status = 'PENDING_VERIFICATION';

-- One VERIFIED primary account per user
CREATE UNIQUE INDEX idx_bank_accounts_one_verified
    ON bank_accounts(user_id) WHERE status = 'VERIFIED';

-- Audit log (append-only)
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_type  VARCHAR(10) NOT NULL CHECK (actor_type IN ('USER','ADMIN','SYSTEM')),
    actor_id    UUID,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   VARCHAR(64) NOT NULL,
    before_data JSONB,
    after_data  JSONB,
    ip          VARCHAR(64),
    user_agent  VARCHAR(500),
    request_id  VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_type, actor_id);
CREATE INDEX idx_audit_log_created ON audit_log(created_at DESC);

-- Append-only enforcement, shared with ledger_entries
CREATE OR REPLACE FUNCTION reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
