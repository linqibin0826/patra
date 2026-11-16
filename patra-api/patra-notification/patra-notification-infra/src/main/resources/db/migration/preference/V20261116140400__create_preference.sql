-- preference 子域：邮件偏好，每个用户一行
-- 写提醒时为会收到邮件的收件人预建默认行（两类都是即时），用户改档位或一键退订时修改。

CREATE TABLE ntf_email_preference
(
    id                BIGINT          NOT NULL,
    user_id           BIGINT          NOT NULL,
    reply_mode        VARCHAR(16)     NOT NULL,
    mention_mode      VARCHAR(16)     NOT NULL,
    unsubscribe_token VARCHAR(43)     NOT NULL,
    record_remarks    jsonb           NULL,
    ip_address        INET            NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT          NULL,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_ntf_email_preference_reply_mode CHECK (reply_mode IN ('OFF', 'INSTANT', 'DIGEST')),
    CONSTRAINT ck_ntf_email_preference_mention_mode CHECK (mention_mode IN ('OFF', 'INSTANT', 'DIGEST')),
    CONSTRAINT ck_ntf_email_preference_token CHECK (unsubscribe_token ~ '^[A-Za-z0-9_-]{43}$')
);
CREATE UNIQUE INDEX uk_ntf_email_preference_user ON ntf_email_preference (user_id);
CREATE UNIQUE INDEX uk_ntf_email_preference_token ON ntf_email_preference (unsubscribe_token);

COMMENT ON TABLE ntf_email_preference IS '邮件偏好：回复我的、@ 我的两类各一个档位，加一个长期有效的一键退订令牌';
COMMENT ON COLUMN ntf_email_preference.user_id IS '用户 ID';
COMMENT ON COLUMN ntf_email_preference.reply_mode IS '「回复我的」档位：OFF、INSTANT（离站时）或 DIGEST（北京时间每日 08:00）';
COMMENT ON COLUMN ntf_email_preference.mention_mode IS '「@ 我的」档位，取值同 reply_mode';
COMMENT ON COLUMN ntf_email_preference.unsubscribe_token IS '长期有效的一键退订令牌：32 字节随机数的 base64url（无填充）；每封提醒邮件都要拼进链接，所以存明文';

CREATE TRIGGER trg_ntf_email_preference_audit_times
    BEFORE INSERT OR UPDATE ON ntf_email_preference
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
