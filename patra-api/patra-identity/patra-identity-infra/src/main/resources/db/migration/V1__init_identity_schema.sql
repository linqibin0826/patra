-- set_updated_at() 触发器函数（每个服务首个脚本独立定义，CREATE OR REPLACE 保证幂等）
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- idn_user：前台用户
-- ============================================================
CREATE TABLE idn_user
(
    id                  BIGINT          NOT NULL,
    email               VARCHAR(254)    NULL,
    email_verified_at   timestamptz(6)  NULL,
    username            VARCHAR(30)     NOT NULL,
    username_changed_at timestamptz(6)  NULL,
    status              VARCHAR(16)     NOT NULL,
    banned_at           timestamptz(6)  NULL,
    deletion_due_at     timestamptz(6)  NULL,
    deleted_at          timestamptz(6)  NULL,
    record_remarks      jsonb           NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    ip_address          bytea           NULL,
    created_at          timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT          NULL,
    created_by_name     VARCHAR(100)    NULL,
    updated_at          timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT          NULL,
    updated_by_name     VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_email UNIQUE (email),
    CONSTRAINT uk_idn_user_username UNIQUE (username),
    CONSTRAINT ck_idn_user_email_lowercase CHECK (email IS NULL OR email = lower(email)),
    CONSTRAINT ck_idn_user_email_deleted CHECK ((status = 'DELETED') = (email IS NULL)),
    CONSTRAINT ck_idn_user_email_verified_at CHECK (email IS NOT NULL OR email_verified_at IS NULL),
    CONSTRAINT ck_idn_user_username_format CHECK (
        username ~ '^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$'
        AND char_length(username) BETWEEN 3 AND 30
        AND username !~ '^[0-9]+$'),
    CONSTRAINT ck_idn_user_status CHECK (status IN ('ACTIVE', 'BANNED', 'PENDING_DELETION', 'DELETED')),
    CONSTRAINT ck_idn_user_banned_at CHECK ((status = 'BANNED') = (banned_at IS NOT NULL)),
    CONSTRAINT ck_idn_user_deletion_due_at CHECK ((status = 'PENDING_DELETION') = (deletion_due_at IS NOT NULL)),
    CONSTRAINT ck_idn_user_deleted_at CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

COMMENT ON TABLE idn_user IS 'Front-end user account (account type USER); rows are kept forever, DELETED rows are anonymized';
COMMENT ON COLUMN idn_user.id IS 'PK (snowflake), never reused';
COMMENT ON COLUMN idn_user.email IS 'Normalized email: trimmed and lower-cased; null only when DELETED';
COMMENT ON COLUMN idn_user.email_verified_at IS 'When the email was verified (UTC); null while unverified';
COMMENT ON COLUMN idn_user.username IS 'Public handle: 3-30 chars of [a-z0-9_-], unique, never released';
COMMENT ON COLUMN idn_user.username_changed_at IS 'Last username change (UTC); null if never changed';
COMMENT ON COLUMN idn_user.status IS 'ACTIVE, BANNED, PENDING_DELETION or DELETED';
COMMENT ON COLUMN idn_user.banned_at IS 'Set when BANNED, null otherwise';
COMMENT ON COLUMN idn_user.deletion_due_at IS 'Set when PENDING_DELETION: when the deletion is executed (UTC)';
COMMENT ON COLUMN idn_user.deleted_at IS 'Set when DELETED: when the deletion was executed (UTC)';
COMMENT ON COLUMN idn_user.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_updated_at
    BEFORE UPDATE ON idn_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- idn_user_password_credential：前台用户的密码凭据
-- ============================================================
CREATE TABLE idn_user_password_credential
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_password_credential_user UNIQUE (user_id),
    CONSTRAINT fk_idn_user_password_credential_user FOREIGN KEY (user_id) REFERENCES idn_user (id)
);

COMMENT ON TABLE idn_user_password_credential IS 'Password credential of a front-end user';
COMMENT ON COLUMN idn_user_password_credential.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_password_credential.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_password_credential.password_hash IS 'Argon2id encoded hash (PHC string)';
COMMENT ON COLUMN idn_user_password_credential.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_password_credential.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_password_credential.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_password_credential.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_password_credential.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_password_credential.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_password_credential.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_password_credential.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_password_credential.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_password_credential_updated_at
    BEFORE UPDATE ON idn_user_password_credential
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- idn_user_login_record：前台用户的登录记录，ID 就是会话 ID
-- ============================================================
CREATE TABLE idn_user_login_record
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    client_type     VARCHAR(16)     NOT NULL,
    device_id       VARCHAR(128)    NULL,
    expires_at      timestamptz(6)  NOT NULL,
    ended_at        timestamptz(6)  NULL,
    end_reason      VARCHAR(16)     NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_idn_user_login_record_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_login_record_client_type CHECK (client_type IN ('WEB')),
    CONSTRAINT ck_idn_user_login_record_end_reason CHECK (end_reason IN ('LOGOUT', 'BANNED', 'REPLACED')),
    CONSTRAINT ck_idn_user_login_record_ended CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

CREATE INDEX idx_idn_user_login_record_user_id ON idn_user_login_record (user_id);

COMMENT ON TABLE idn_user_login_record IS 'Login record of a front-end user; id equals the session id';
COMMENT ON COLUMN idn_user_login_record.id IS 'PK (snowflake), same as the Redis session id';
COMMENT ON COLUMN idn_user_login_record.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_login_record.client_type IS 'Client type: WEB';
COMMENT ON COLUMN idn_user_login_record.device_id IS 'Client-reported device identifier, optional';
COMMENT ON COLUMN idn_user_login_record.expires_at IS 'Absolute expiry of the session (UTC)';
COMMENT ON COLUMN idn_user_login_record.ended_at IS 'When the session ended explicitly; null while open or after a silent expiry';
COMMENT ON COLUMN idn_user_login_record.end_reason IS 'LOGOUT, BANNED or REPLACED';
COMMENT ON COLUMN idn_user_login_record.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_login_record.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_login_record.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_login_record.created_at IS 'Creation time (UTC), also the login time';
COMMENT ON COLUMN idn_user_login_record.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_login_record.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_login_record.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_login_record.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_login_record.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_login_record_updated_at
    BEFORE UPDATE ON idn_user_login_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- idn_user_profile：前台用户的资料，与用户一对一（本 Issue 只有昵称，PAP-95 补其余列）
-- ============================================================
CREATE TABLE idn_user_profile
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    display_name    VARCHAR(120)    NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_profile_user UNIQUE (user_id),
    CONSTRAINT fk_idn_user_profile_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_profile_display_name CHECK (
        display_name IS NULL
        OR (octet_length(display_name) BETWEEN 2 AND 120 AND display_name = btrim(display_name)))
);

COMMENT ON TABLE idn_user_profile IS 'Profile of a front-end user, one row per user';
COMMENT ON COLUMN idn_user_profile.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_profile.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_profile.display_name IS 'Display name (NFC, trimmed); null means follow the username';
COMMENT ON COLUMN idn_user_profile.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_profile.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_profile.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_profile.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_profile.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_profile.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_profile.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_profile.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_profile.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_profile_updated_at
    BEFORE UPDATE ON idn_user_profile
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- idn_user_former_username：改名留下的旧名。reserved_until 之前只有原主能改回、别人不能注册或改成它，
-- /users/旧名 跳到新名；到期后行留作审计。不加唯一约束：同一个旧名可以在不同时期属于不同的人
-- ============================================================
CREATE TABLE idn_user_former_username
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    username        VARCHAR(30)     NOT NULL,
    replaced_at     timestamptz(6)  NOT NULL,
    reserved_until  timestamptz(6)  NOT NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_idn_user_former_username_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_former_username_format CHECK (
        username ~ '^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$'
        AND char_length(username) BETWEEN 3 AND 30
        AND username !~ '^[0-9]+$'),
    CONSTRAINT ck_idn_user_former_username_period CHECK (reserved_until > replaced_at)
);

CREATE INDEX idx_idn_user_former_username_username
    ON idn_user_former_username (username, reserved_until);
CREATE INDEX idx_idn_user_former_username_user_id ON idn_user_former_username (user_id);

COMMENT ON TABLE idn_user_former_username IS 'Former username left behind by a rename; reserved for its owner until reserved_until';
COMMENT ON COLUMN idn_user_former_username.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_former_username.user_id IS 'Former owner user ID';
COMMENT ON COLUMN idn_user_former_username.username IS 'The former username (normalized, lower-case)';
COMMENT ON COLUMN idn_user_former_username.replaced_at IS 'When the rename happened (UTC)';
COMMENT ON COLUMN idn_user_former_username.reserved_until IS 'Reserved for the former owner until this time (UTC), exclusive';
COMMENT ON COLUMN idn_user_former_username.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_former_username.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_former_username.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_former_username.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_former_username.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_former_username.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_former_username.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_former_username.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_former_username.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_former_username_updated_at
    BEFORE UPDATE ON idn_user_former_username
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
