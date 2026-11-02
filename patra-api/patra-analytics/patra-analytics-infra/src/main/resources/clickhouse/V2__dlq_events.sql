-- 不合格事件（入库设计 §5.2）：dlq-record 的字段；user_id 从原文取出，注销清理按它删。
CREATE TABLE IF NOT EXISTS dlq_events (
    received_at     DateTime64(3, 'UTC'),
    reason_code     LowCardinality(String),
    reason_detail   String,
    event_name      LowCardinality(String),
    raw             String,
    raw_truncated   Bool,
    user_id         UInt64 MATERIALIZED toUInt64OrZero(JSONExtractString(raw, 'user_id'))
) ENGINE = MergeTree
PARTITION BY toYYYYMMDD(received_at, 'Asia/Shanghai')
ORDER BY (reason_code, received_at)
TTL toDateTime(received_at) + INTERVAL 90 DAY DELETE
SETTINGS non_replicated_deduplication_window = 1000, ttl_only_drop_parts = 1;
