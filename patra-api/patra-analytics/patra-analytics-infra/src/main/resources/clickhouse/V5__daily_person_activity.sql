-- 日活（入库设计 §5.5）：每人每天（北京时间）一行；保留 400 天，日活、留存都从这里算。
CREATE TABLE IF NOT EXISTS daily_person_activity (
    day           Date,
    person_id     String,
    user_id       SimpleAggregateFunction(max, UInt64),   -- 同一个 person_id 的 user_id 不变；26.x 不许 AggregatingMergeTree 有非键非聚合列
    is_synthetic  Bool,
    is_bot        SimpleAggregateFunction(max, UInt8),
    has_client    SimpleAggregateFunction(max, UInt8),
    first_seen    SimpleAggregateFunction(min, DateTime64(3, 'UTC'))
) ENGINE = AggregatingMergeTree
ORDER BY (day, person_id, is_synthetic)
TTL day + INTERVAL 400 DAY DELETE
SETTINGS non_replicated_deduplication_window = 1000;

CREATE MATERIALIZED VIEW IF NOT EXISTS daily_person_activity_mv TO daily_person_activity AS
SELECT
    toDate(derived_at, 'Asia/Shanghai') AS day,
    person_id,
    user_id,
    is_synthetic,
    is_bot,
    source = 'client' AS has_client,
    derived_at AS first_seen
FROM events;
