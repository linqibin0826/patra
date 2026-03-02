-- LetPub 期刊评价数据（JSON 列）
-- 存储 JCR/CAS 分区、审稿速度、APC 费用等 LetPub 爬取的评价指标
ALTER TABLE cat_venue
    ADD COLUMN letpub_data JSON NULL DEFAULT NULL
        COMMENT 'LetPub期刊评价数据(JSON):JCR/CAS分区、审稿速度、APC等';
