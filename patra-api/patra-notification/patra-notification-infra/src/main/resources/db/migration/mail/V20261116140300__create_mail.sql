-- mail 子域：安全邮件的发送记录、离站提醒的排队与投递

-- ============================================================
-- ntf_mail_delivery：安全邮件的发送记录，按上游事件 ID 去重
-- ============================================================
CREATE TABLE ntf_mail_delivery
(
    id                BIGINT          NOT NULL,
    event_id          UUID            NOT NULL,
    template          TEXT            NOT NULL,
    outcome           VARCHAR(16)     NOT NULL,
    recorded_at       timestamptz(6)  NOT NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_ntf_mail_delivery_outcome CHECK (outcome IN ('SENT', 'LINK_EXPIRED', 'REJECTED'))
);
CREATE UNIQUE INDEX uk_ntf_mail_delivery_event ON ntf_mail_delivery (event_id);

COMMENT ON TABLE ntf_mail_delivery IS '安全邮件（重设密码、验证邮箱等）的发送结果，同一个上游事件只发一次';
COMMENT ON COLUMN ntf_mail_delivery.event_id IS 'identity 的事件 ID，也是服务商的幂等键';
COMMENT ON COLUMN ntf_mail_delivery.template IS '哪一类信（模板名）';
COMMENT ON COLUMN ntf_mail_delivery.outcome IS '结果：SENT、LINK_EXPIRED（链接已过期不发）或 REJECTED（服务商拒收）';
COMMENT ON COLUMN ntf_mail_delivery.recorded_at IS '得出结果的时间';

CREATE TRIGGER trg_ntf_mail_delivery_audit_times
    BEFORE INSERT OR UPDATE ON ntf_mail_delivery
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- ntf_reminder_queue：回复、@ 写入信箱后排队，10 分钟后看收件人回没回来过
-- ============================================================
CREATE TABLE ntf_reminder_queue
(
    id                BIGINT          NOT NULL,
    notification_id   BIGINT          NOT NULL,
    recipient_user_id BIGINT          NOT NULL,
    category          VARCHAR(16)     NOT NULL,
    queued_at         timestamptz(6)  NOT NULL,
    due_at            timestamptz(6)  NOT NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_ntf_reminder_queue_category CHECK (category IN ('REPLY', 'MENTION'))
);
CREATE UNIQUE INDEX uk_ntf_reminder_queue_notification ON ntf_reminder_queue (notification_id);
CREATE INDEX idx_ntf_reminder_queue_due ON ntf_reminder_queue (due_at);
CREATE INDEX idx_ntf_reminder_queue_recipient ON ntf_reminder_queue (recipient_user_id);

COMMENT ON TABLE ntf_reminder_queue IS '离站提醒的排队：回复与 @ 写入信箱时入队，到点后按收件人合并成投递';
COMMENT ON COLUMN ntf_reminder_queue.notification_id IS '信箱里的提醒（ntf_notification.id）';
COMMENT ON COLUMN ntf_reminder_queue.recipient_user_id IS '收件人用户 ID';
COMMENT ON COLUMN ntf_reminder_queue.category IS 'REPLY 或 MENTION';
COMMENT ON COLUMN ntf_reminder_queue.queued_at IS '提醒写入信箱的时间；和最后在线时间比较';
COMMENT ON COLUMN ntf_reminder_queue.due_at IS 'queued_at 加 10 分钟';

CREATE TRIGGER trg_ntf_reminder_queue_audit_times
    BEFORE INSERT OR UPDATE ON ntf_reminder_queue
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- ntf_reminder_delivery：一封提醒邮件的投递（离站批量或每日汇总），失败按退避重试
-- ============================================================
CREATE TABLE ntf_reminder_delivery
(
    id                BIGINT          NOT NULL,
    recipient_user_id BIGINT          NOT NULL,
    kind              VARCHAR(16)     NOT NULL,
    category          VARCHAR(16)     NULL,
    notification_ids  BIGINT[]        NULL,
    window_end        timestamptz(6)  NULL,
    status            VARCHAR(16)     NOT NULL,
    skip_reason       VARCHAR(32)     NULL,
    attempts          INTEGER         NOT NULL DEFAULT 0,
    next_attempt_at   timestamptz(6)  NOT NULL,
    first_sent_at     timestamptz(6)  NULL,
    finished_at       timestamptz(6)  NULL,
    record_remarks    jsonb           NULL,
    ip_address        INET            NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT          NULL,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_ntf_reminder_delivery_kind CHECK (
        (kind = 'INSTANT' AND category IN ('REPLY', 'MENTION') AND notification_ids IS NOT NULL
            AND window_end IS NULL)
        OR (kind = 'DIGEST' AND category IS NULL AND notification_ids IS NULL AND window_end IS NOT NULL)),
    CONSTRAINT ck_ntf_reminder_delivery_status CHECK (status IN ('PENDING', 'SENT', 'SKIPPED', 'EXPIRED')),
    CONSTRAINT ck_ntf_reminder_delivery_finished CHECK ((status = 'PENDING') = (finished_at IS NULL)),
    CONSTRAINT ck_ntf_reminder_delivery_skip_reason CHECK (
        (status = 'SKIPPED') = (skip_reason IS NOT NULL)
        AND (skip_reason IS NULL OR skip_reason IN ('MODE', 'NOTHING_UNREAD', 'RECIPIENT', 'REJECTED')))
);
CREATE UNIQUE INDEX uk_ntf_reminder_delivery_digest ON ntf_reminder_delivery (recipient_user_id, window_end) WHERE kind = 'DIGEST';
CREATE INDEX idx_ntf_reminder_delivery_pending ON ntf_reminder_delivery (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_ntf_reminder_delivery_finished ON ntf_reminder_delivery (finished_at) WHERE finished_at IS NOT NULL;

COMMENT ON TABLE ntf_reminder_delivery IS '提醒邮件的投递：离站批量（INSTANT）或每日 08:00 汇总（DIGEST）；id 也是服务商幂等键与 Message-ID';
COMMENT ON COLUMN ntf_reminder_delivery.recipient_user_id IS '收件人用户 ID';
COMMENT ON COLUMN ntf_reminder_delivery.kind IS 'INSTANT（离站批量）或 DIGEST（北京时间每日 08:00 汇总）';
COMMENT ON COLUMN ntf_reminder_delivery.category IS '只用于 INSTANT：REPLY 或 MENTION';
COMMENT ON COLUMN ntf_reminder_delivery.notification_ids IS '只用于 INSTANT：合进这封信的提醒，按入队顺序';
COMMENT ON COLUMN ntf_reminder_delivery.window_end IS '只用于 DIGEST：24 小时窗口的结束时刻';
COMMENT ON COLUMN ntf_reminder_delivery.status IS '状态：PENDING、SENT、SKIPPED 或 EXPIRED';
COMMENT ON COLUMN ntf_reminder_delivery.skip_reason IS '只用于 SKIPPED：MODE、NOTHING_UNREAD、RECIPIENT 或 REJECTED';
COMMENT ON COLUMN ntf_reminder_delivery.attempts IS '已失败的次数';
COMMENT ON COLUMN ntf_reminder_delivery.next_attempt_at IS '下一次尝试的最早时间';
COMMENT ON COLUMN ntf_reminder_delivery.first_sent_at IS '第一次调用发信端口的时间；24 小时后不再重试';
COMMENT ON COLUMN ntf_reminder_delivery.finished_at IS '变成 SENT、SKIPPED 或 EXPIRED 的时间；保留 7 天';

CREATE TRIGGER trg_ntf_reminder_delivery_audit_times
    BEFORE INSERT OR UPDATE ON ntf_reminder_delivery
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
