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
