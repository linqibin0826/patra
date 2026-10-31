-- ============================================================
-- preference 子域：每个用户一行提醒邮件偏好（PAP-142，release spec v0.16 2.1）
-- 没有行 = 默认值（两类都是离站逐条）；第一次改档位、或 mail 第一次为他取退订令牌时才建行。
-- 退订令牌存原文：每封提醒邮件都要把它拼进退订链接，存哈希就拼不出来（工程 spec 6.2）。
-- 只存用户 ID；注销清理删整行，令牌随之作废。
-- 不挂 set_updated_at：每条写入都显式给 updated_at（应用注入的时钟），同 collection.collection。
-- ============================================================
CREATE TABLE preference.email_preference
(
    user_id            BIGINT          NOT NULL,
    reply_mode         VARCHAR(16)     NOT NULL,
    mention_mode       VARCHAR(16)     NOT NULL,
    unsubscribe_token  VARCHAR(43)     NOT NULL,
    created_at         timestamptz(6)  NOT NULL,
    updated_at         timestamptz(6)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_email_preference_token UNIQUE (unsubscribe_token),
    CONSTRAINT ck_email_preference_reply_mode CHECK (reply_mode IN ('OFF', 'INSTANT', 'DIGEST')),
    CONSTRAINT ck_email_preference_mention_mode CHECK (mention_mode IN ('OFF', 'INSTANT', 'DIGEST')),
    CONSTRAINT ck_email_preference_token CHECK (unsubscribe_token ~ '^[A-Za-z0-9_-]{43}$')
);

COMMENT ON TABLE preference.email_preference IS 'Reminder email preferences, one row per user; a missing row means the defaults (INSTANT for both categories)';
COMMENT ON COLUMN preference.email_preference.user_id IS 'Identity user ID';
COMMENT ON COLUMN preference.email_preference.reply_mode IS 'Email mode for "replies to me": OFF, INSTANT (when away) or DIGEST (daily at 08:00 Beijing time)';
COMMENT ON COLUMN preference.email_preference.mention_mode IS 'Email mode for "mentions of me", same values as reply_mode';
COMMENT ON COLUMN preference.email_preference.unsubscribe_token IS 'Long-lived one-click unsubscribe token: 32 random bytes, base64url without padding; stored in plain text because every reminder email embeds it';
COMMENT ON COLUMN preference.email_preference.created_at IS 'When the row was created (UTC)';
COMMENT ON COLUMN preference.email_preference.updated_at IS 'Last change of either mode (UTC)';
