-- ============================================================
-- 站内提醒搬到 patra-notification（v0.16 PAP-143，release spec 决策 A）：删掉 interaction 的 notification schema
-- 与其中的提醒表，旧提醒不迁移（release spec 第 3 节 L）。
-- 本目录的三个旧脚本保留，只为 Flyway 历史：它们已在 mini 上执行过，删掉脚本会让校验失败、服务起不来。
-- ============================================================
DROP SCHEMA notification CASCADE;
