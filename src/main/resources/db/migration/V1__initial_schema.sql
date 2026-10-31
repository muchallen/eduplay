-- Eduplay initial schema.
-- All timestamps are TIMESTAMPTZ and written in UTC by the application.

CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(254) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    full_name         VARCHAR(120) NOT NULL,
    phone             VARCHAR(32),
    role              VARCHAR(16)  NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('PARENT', 'TEACHER', 'ADMIN'))
);

-- An email is unique among active accounts; a soft-deleted account awaiting purge
-- does not block the parent from registering again.
CREATE UNIQUE INDEX ux_users_email_active ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_deleted_at ON users (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL,
    device_label VARCHAR(100),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_password_reset_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);

CREATE TABLE email_verification_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_email_verification_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_email_verification_tokens_user_id ON email_verification_tokens (user_id);

CREATE TABLE consent_records (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    policy_version VARCHAR(32) NOT NULL,
    accepted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address     VARCHAR(45)
);
CREATE INDEX ix_consent_records_user_id ON consent_records (user_id);

CREATE TABLE children (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    display_name VARCHAR(40) NOT NULL,
    birth_year   INTEGER,
    avatar_id    VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX ix_children_parent_id ON children (parent_id);
CREATE INDEX ix_children_deleted_at ON children (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE lesson_progress (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id           UUID        NOT NULL REFERENCES children (id) ON DELETE CASCADE,
    section_id         VARCHAR(64) NOT NULL,
    topic_id           VARCHAR(64) NOT NULL,
    lesson_id          VARCHAR(64) NOT NULL,
    current_game_index INTEGER     NOT NULL DEFAULT 0,
    completed          BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at       TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_lesson_progress_lesson UNIQUE (child_id, section_id, topic_id, lesson_id),
    CONSTRAINT ck_lesson_progress_game_index CHECK (current_game_index >= 0)
);
CREATE INDEX ix_lesson_progress_child_id ON lesson_progress (child_id);

CREATE TABLE game_results (
    id               UUID PRIMARY KEY,
    child_id         UUID        NOT NULL REFERENCES children (id) ON DELETE CASCADE,
    section_id       VARCHAR(64) NOT NULL,
    topic_id         VARCHAR(64) NOT NULL,
    lesson_id        VARCHAR(64) NOT NULL,
    game_index       INTEGER     NOT NULL,
    game_type        VARCHAR(64) NOT NULL,
    score            INTEGER     NOT NULL,
    attempts         INTEGER     NOT NULL,
    mistakes         INTEGER     NOT NULL,
    duration_seconds INTEGER     NOT NULL,
    completed        BOOLEAN     NOT NULL,
    played_at        TIMESTAMPTZ NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_game_results_non_negative CHECK (
        game_index >= 0 AND score >= 0 AND attempts >= 0 AND mistakes >= 0 AND duration_seconds >= 0)
);
CREATE INDEX ix_game_results_child_played_at ON game_results (child_id, played_at);

-- Game results are append-only: rows may be inserted or purged, never modified.
CREATE FUNCTION prevent_game_result_update() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'game_results is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_game_results_append_only
    BEFORE UPDATE ON game_results
    FOR EACH ROW EXECUTE FUNCTION prevent_game_result_update();

CREATE TABLE subscriptions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan               VARCHAR(32) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    starts_at          TIMESTAMPTZ NOT NULL,
    ends_at            TIMESTAMPTZ,
    provider           VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_subscriptions_user UNIQUE (user_id),
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED'))
);
CREATE INDEX ix_subscriptions_status_ends_at ON subscriptions (status, ends_at);
