-- 邮件发送记录（PAP-105）：每封信处理完记一行，发信前按投递 ID 查重。
-- 不存正文、链接、收件地址与用户 ID：通知服务不存邮箱副本（release spec 决策 F），表里没有个人数据。
-- 只追加、不更新，没有 updated_at。发送失败不进表（那次处理回滚了，看日志与死信队列）。
CREATE SCHEMA mail;

CREATE TABLE mail.mail_delivery
(
    delivery_id UUID                     NOT NULL PRIMARY KEY, -- 投递 ID，也是 Resend 幂等键；安全邮件就是 identity 的事件 ID
    template    TEXT                     NOT NULL,             -- 哪一类信（模板名）
    outcome     TEXT                     NOT NULL CHECK (outcome IN ('SENT', 'LINK_EXPIRED')),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
