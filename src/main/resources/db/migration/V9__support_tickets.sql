-- V9: Member support tickets with a message thread.

CREATE TABLE support_tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    subject         VARCHAR(150) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
    priority        VARCHAR(10) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('NORMAL','HIGH')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    resolved_by     UUID REFERENCES admin_users(id),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_support_tickets_user ON support_tickets(user_id, last_message_at DESC);
CREATE INDEX idx_support_tickets_queue ON support_tickets(status, priority, last_message_at);

CREATE TABLE support_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID NOT NULL REFERENCES support_tickets(id),
    author_type VARCHAR(10) NOT NULL CHECK (author_type IN ('USER','ADMIN')),
    author_id   UUID NOT NULL,
    body        VARCHAR(4000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_messages_ticket ON support_messages(ticket_id, created_at);
