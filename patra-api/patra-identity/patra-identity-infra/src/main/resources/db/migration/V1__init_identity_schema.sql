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
    id              BIGINT          NOT NULL,
    email           VARCHAR(254)    NOT NULL,
    status          VARCHAR(16)     NOT NULL,
    banned_at       timestamptz(6)  NULL,
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
    CONSTRAINT uk_idn_user_email UNIQUE (email),
    CONSTRAINT ck_idn_user_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_idn_user_status CHECK (status IN ('ACTIVE', 'BANNED')),
    CONSTRAINT ck_idn_user_banned_at CHECK ((status = 'BANNED') = (banned_at IS NOT NULL))
);

COMMENT ON TABLE idn_user IS 'Front-end user account (account type USER)';
COMMENT ON COLUMN idn_user.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user.email IS 'Normalized email: trimmed and lower-cased';
COMMENT ON COLUMN idn_user.status IS 'ACTIVE or BANNED';
COMMENT ON COLUMN idn_user.banned_at IS 'Set when BANNED, null otherwise';
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
