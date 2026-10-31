-- ============================================================
-- kernel 底座：在站信号（PAP-143，决策 H）
-- 每个用户一行最后出现在站上的时刻：门户拉未读数、推送连接心跳时写，提醒邮件判断「离站」时读。
-- 存表而不放内存：每次部署都会重启服务，放内存会在提醒的 10 分钟窗口里丢掉记录、多发邮件。
-- 只存用户 ID；注销清理删整行。
-- ============================================================
CREATE SCHEMA kernel;

CREATE TABLE kernel.presence
(
    user_id       BIGINT          NOT NULL,
    last_seen_at  timestamptz(6)  NOT NULL,
    PRIMARY KEY (user_id)
);

COMMENT ON TABLE kernel.presence IS 'When each user was last seen on the site (unread-count poll or push heartbeat); read by away-email checks';
COMMENT ON COLUMN kernel.presence.user_id IS 'Identity user ID';
COMMENT ON COLUMN kernel.presence.last_seen_at IS 'Last time the user was seen (UTC); only moves forward';
