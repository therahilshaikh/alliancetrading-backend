-- V8: user_devices (push tokens)

CREATE TABLE user_devices (
    user_id     UUID PRIMARY KEY REFERENCES users(id),
    push_token  VARCHAR(500) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);