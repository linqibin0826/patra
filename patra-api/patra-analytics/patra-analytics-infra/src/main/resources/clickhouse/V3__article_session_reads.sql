-- 文献 × 会话（入库设计 §5.3）：只存「有没有过」，同一事件来两次结果一样；长期保留。
-- 爬虫事件也写进来，排除留给查询（与 session_quality JOIN）。
CREATE TABLE IF NOT EXISTS article_session_reads (
    article_id    UInt64,
    session_id    UUID,
    person_id     String,
    day           Date,
    is_synthetic  Bool,
    viewed        SimpleAggregateFunction(max, UInt8),
    fulltext      SimpleAggregateFunction(max, UInt8)
) ENGINE = AggregatingMergeTree
ORDER BY (article_id, session_id, person_id, day, is_synthetic)
SETTINGS non_replicated_deduplication_window = 1000;

-- 查看类事件（埋点方案 §6.8）；全文 = 全文阅读或下载 PDF。列名写明：SELECT * 不含计算列
CREATE MATERIALIZED VIEW IF NOT EXISTS article_session_reads_mv TO article_session_reads AS
SELECT
    article_id,
    session_id,
    person_id,
    toDate(derived_at, 'Asia/Shanghai') AS day,
    is_synthetic,
    1 AS viewed,
    event_name IN ('fulltext_read', 'pdf_downloaded') AS fulltext
FROM events
WHERE article_id != 0
  AND event_name IN ('article_viewed', 'fulltext_read', 'pdf_downloaded', 'article_collected');
