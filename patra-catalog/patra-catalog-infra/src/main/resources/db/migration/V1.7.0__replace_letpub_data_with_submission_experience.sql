-- 移除 LetPub JSON 整体存储列
-- 评级数据（IF/JCR/CAS）迁移到 cat_venue_rating 表
ALTER TABLE cat_venue
    DROP COLUMN letpub_data;

-- 添加 LetPub 抓取时间戳，用于批处理断点续传
-- NULL = 未抓取，非 NULL = 已抓取
ALTER TABLE cat_venue
    ADD COLUMN letpub_fetched_at TIMESTAMP(6) NULL DEFAULT NULL
        COMMENT 'LetPub数据抓取时间(UTC),NULL表示未抓取';
