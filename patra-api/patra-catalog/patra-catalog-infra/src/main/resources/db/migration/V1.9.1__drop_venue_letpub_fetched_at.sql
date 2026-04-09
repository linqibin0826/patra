-- 移除 letpub_fetched_at 列
-- 断点续传机制改为 NOT EXISTS 子查询（检查 JCR 评级表是否存在目标年份数据），
-- 不再依赖 VenueEntity 上的标记字段。
ALTER TABLE cat_venue DROP COLUMN letpub_fetched_at;
