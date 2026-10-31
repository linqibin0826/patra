-- ============================================================
-- mail 子域：提醒邮件（PAP-146，工程 spec 第 6 节）
-- reminder_queue：在等 10 分钟的提醒，与信箱写入同一事务入队，到点后丢弃或合成投递。
-- reminder_delivery：一封提醒邮件的投递记录；投递 ID 是发信端口的幂等键，重试不变。
-- 只存用户 ID 与提醒 ID，不存收件地址与正文（决策 F）。时间都由代码显式给（注入的时钟），不挂触发器。
-- schema 用 IF NOT EXISTS：安全邮件的发送记录（PAP-105）也在 mail 下，两边的迁移谁先跑都行。
-- ============================================================
CREATE SCHEMA IF NOT EXISTS mail;

CREATE TABLE mail.reminder_queue
(
    notification_id  BIGINT          NOT NULL,
    recipient_id     BIGINT          NOT NULL,
    category         VARCHAR(16)     NOT NULL,
    queued_at        timestamptz(6)  NOT NULL,
    due_at           timestamptz(6)  NOT NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT ck_reminder_queue_category CHECK (category IN ('REPLY', 'MENTION'))
);

COMMENT ON TABLE mail.reminder_queue IS 'Reminders waiting 10 minutes before an away-from-site email is considered';
COMMENT ON COLUMN mail.reminder_queue.notification_id IS 'Inbox notification ID';
COMMENT ON COLUMN mail.reminder_queue.recipient_id IS 'Identity user ID of the recipient';
COMMENT ON COLUMN mail.reminder_queue.category IS 'REPLY or MENTION';
COMMENT ON COLUMN mail.reminder_queue.queued_at IS 'When the notification entered the inbox (UTC); presence is compared against it';
COMMENT ON COLUMN mail.reminder_queue.due_at IS 'queued_at plus 10 minutes (UTC)';

CREATE INDEX idx_reminder_queue_due ON mail.reminder_queue (due_at);
CREATE INDEX idx_reminder_queue_recipient ON mail.reminder_queue (recipient_id);

CREATE TABLE mail.reminder_delivery
(
    delivery_id       UUID            NOT NULL,
    recipient_id      BIGINT          NOT NULL,
    kind              VARCHAR(16)     NOT NULL,
    category          VARCHAR(16)     NULL,
    notification_ids  BIGINT[]        NULL,
    window_end        timestamptz(6)  NULL,
    status            VARCHAR(16)     NOT NULL,
    skip_reason       VARCHAR(32)     NULL,
    attempts          INTEGER         NOT NULL,
    next_attempt_at   timestamptz(6)  NOT NULL,
    created_at        timestamptz(6)  NOT NULL,
    first_sent_at     timestamptz(6)  NULL,
    finished_at       timestamptz(6)  NULL,
    PRIMARY KEY (delivery_id),
    CONSTRAINT ck_reminder_delivery_kind CHECK (
        (kind = 'INSTANT' AND category IN ('REPLY', 'MENTION') AND notification_ids IS NOT NULL
            AND window_end IS NULL)
        OR (kind = 'DIGEST' AND category IS NULL AND notification_ids IS NULL AND window_end IS NOT NULL)),
    CONSTRAINT ck_reminder_delivery_status CHECK (status IN ('PENDING', 'SENT', 'SKIPPED', 'EXPIRED')),
    CONSTRAINT ck_reminder_delivery_finished CHECK ((status = 'PENDING') = (finished_at IS NULL)),
    CONSTRAINT ck_reminder_delivery_skip_reason CHECK (
        (status = 'SKIPPED') = (skip_reason IS NOT NULL)
        AND (skip_reason IS NULL OR skip_reason IN ('MODE', 'NOTHING_UNREAD', 'RECIPIENT', 'REJECTED')))
);

COMMENT ON TABLE mail.reminder_delivery IS 'One reminder email: its delivery ID is the idempotency key reused on every retry';
COMMENT ON COLUMN mail.reminder_delivery.delivery_id IS 'Delivery ID, used as Resend-Idempotency-Key and Message-ID';
COMMENT ON COLUMN mail.reminder_delivery.recipient_id IS 'Identity user ID of the recipient';
COMMENT ON COLUMN mail.reminder_delivery.kind IS 'INSTANT (away-from-site batch) or DIGEST (daily at 08:00 Beijing time)';
COMMENT ON COLUMN mail.reminder_delivery.category IS 'INSTANT only: REPLY or MENTION';
COMMENT ON COLUMN mail.reminder_delivery.notification_ids IS 'INSTANT only: notifications batched into this email, in queue order';
COMMENT ON COLUMN mail.reminder_delivery.window_end IS 'DIGEST only: end of the 24-hour window (UTC)';
COMMENT ON COLUMN mail.reminder_delivery.status IS 'PENDING, SENT, SKIPPED or EXPIRED';
COMMENT ON COLUMN mail.reminder_delivery.skip_reason IS 'SKIPPED only: MODE, NOTHING_UNREAD, RECIPIENT or REJECTED';
COMMENT ON COLUMN mail.reminder_delivery.attempts IS 'Failed attempts so far';
COMMENT ON COLUMN mail.reminder_delivery.next_attempt_at IS 'Earliest time of the next attempt (UTC)';
COMMENT ON COLUMN mail.reminder_delivery.created_at IS 'When the record was created (UTC)';
COMMENT ON COLUMN mail.reminder_delivery.first_sent_at IS 'First call to the mail delivery port (UTC); retries stop 24 hours later';
COMMENT ON COLUMN mail.reminder_delivery.finished_at IS 'When it became SENT, SKIPPED or EXPIRED (UTC); kept 7 days';

-- 一个人一个汇总窗口只有一封（部分唯一索引；ON CONFLICT 要带同样的谓词）
CREATE UNIQUE INDEX uk_reminder_delivery_digest ON mail.reminder_delivery (recipient_id, window_end) WHERE kind = 'DIGEST';
CREATE INDEX idx_reminder_delivery_pending ON mail.reminder_delivery (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_reminder_delivery_finished ON mail.reminder_delivery (finished_at) WHERE finished_at IS NOT NULL;
