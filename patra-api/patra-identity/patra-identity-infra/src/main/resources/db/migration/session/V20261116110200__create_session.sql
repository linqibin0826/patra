-- session 子域：登录记录

-- ============================================================
-- idn_user_login_record：前台用户的登录记录，ID 就是会话 ID
-- ============================================================
CREATE TABLE idn_user_login_record
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    client_type     VARCHAR(16)     NOT NULL,
    device_id       VARCHAR(43)     NULL,
    user_agent      VARCHAR(512)    NULL,
    device_name     VARCHAR(64)     NULL,
    device_class    VARCHAR(16)     NOT NULL,
    client_ip       VARCHAR(45)     NULL,
    ip_region       VARCHAR(64)     NULL,
    expires_at      timestamptz(6)  NOT NULL,
    ended_at        timestamptz(6)  NULL,
    end_reason      VARCHAR(32)     NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_idn_user_login_record_client_type CHECK (client_type IN ('WEB')),
    CONSTRAINT ck_idn_user_login_record_device_class CHECK (device_class IN ('DESKTOP', 'MOBILE', 'TABLET', 'UNKNOWN')),
    CONSTRAINT ck_idn_user_login_record_end_reason CHECK (end_reason IN ('LOGOUT', 'BANNED', 'REPLACED', 'REVOKED',
        'EMAIL_VERIFIED', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'EMAIL_REVERTED', 'DELETION_REQUESTED',
        'DELETION_COMPLETED')),
    CONSTRAINT ck_idn_user_login_record_ended CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

-- 新设备判定按账号 + 设备查（前缀也覆盖按账号查）；清理任务按「结束或过期的时刻」删
CREATE INDEX idx_idn_user_login_record_user_device ON idn_user_login_record (user_id, device_id);
CREATE INDEX idx_idn_user_login_record_retire_at ON idn_user_login_record ((COALESCE(ended_at, expires_at)));

COMMENT ON TABLE idn_user_login_record IS '前台用户的登录记录，ID 就是会话 ID；设备、User-Agent、IP 都是登录当时的值';
COMMENT ON COLUMN idn_user_login_record.user_id IS '所属用户';
COMMENT ON COLUMN idn_user_login_record.client_type IS '客户端类型：WEB';
COMMENT ON COLUMN idn_user_login_record.device_id IS '门户 Cookie 里的设备标识，43 位 base64url，可空';
COMMENT ON COLUMN idn_user_login_record.user_agent IS '登录时的 User-Agent，去掉控制字符、截到 512 个码点';
COMMENT ON COLUMN idn_user_login_record.device_name IS 'Yauaa 解析出的浏览器大版本与系统，如 Chrome 141 · macOS；解析不出为空';
COMMENT ON COLUMN idn_user_login_record.device_class IS '设备类型：DESKTOP、MOBILE、TABLET、UNKNOWN';
COMMENT ON COLUMN idn_user_login_record.client_ip IS '经可信转发链得到的登录 IP；不是合法字面量时为空';
COMMENT ON COLUMN idn_user_login_record.ip_region IS 'ip2region 查出的地区（国内到省市，国外只到国家）；内网或查不到为空';
COMMENT ON COLUMN idn_user_login_record.expires_at IS '会话的绝对过期时间';
COMMENT ON COLUMN idn_user_login_record.ended_at IS '会话被明确结束的时间；还开着或静默过期时为空';
COMMENT ON COLUMN idn_user_login_record.end_reason IS '结束原因：LOGOUT、BANNED、REPLACED、REVOKED（在设备列表里被踢）、EMAIL_VERIFIED、PASSWORD_CHANGED、PASSWORD_RESET、EMAIL_REVERTED、DELETION_REQUESTED、DELETION_COMPLETED';

CREATE TRIGGER trg_idn_user_login_record_audit_times
    BEFORE INSERT OR UPDATE ON idn_user_login_record
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
