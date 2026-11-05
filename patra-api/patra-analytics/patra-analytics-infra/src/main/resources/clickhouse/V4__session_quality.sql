-- 会话特征（入库设计 §5.4）：真人会话 = 有浏览器事件且没有爬虫事件，在查询时判定；长期保留。
CREATE TABLE IF NOT EXISTS session_quality (
    session_id    UUID,
    has_client    SimpleAggregateFunction(max, UInt8),
    has_bot       SimpleAggregateFunction(max, UInt8),
    first_day     SimpleAggregateFunction(min, Date)
) ENGINE = AggregatingMergeTree
ORDER BY session_id
SETTINGS non_replicated_deduplication_window = 1000;

CREATE MATERIALIZED VIEW IF NOT EXISTS session_quality_mv TO session_quality AS
SELECT
    session_id,
    source = 'client' AS has_client,
    toUInt8(is_bot) AS has_bot,
    toDate(derived_at, 'Asia/Shanghai') AS first_day
FROM events;
