-- 事件明细（入库设计 §5.1）：一行一条富化事件，不存 IP 与原始 UA。
-- ReplacingMergeTree + 排序键末尾 event_id：合并时同一事件只留一行；数条数的查询一律 uniqExact(event_id)。
-- 「天」按北京时间切；按天分区，90 天后整个分区丢掉。
CREATE TABLE IF NOT EXISTS events (
    event_id        UUID,
    event_name      LowCardinality(String),
    schema_version  LowCardinality(String),
    source          LowCardinality(String),
    created_at      DateTime64(3, 'UTC'),
    sent_at         DateTime64(3, 'UTC'),
    received_at     DateTime64(3, 'UTC'),
    derived_at      DateTime64(3, 'UTC'),
    device_id       String,
    session_id      UUID,
    user_id         UInt64,
    page_path       String,
    page_type       LowCardinality(String),
    referrer_host   String,
    country_code    LowCardinality(String),
    province        LowCardinality(String),
    browser_family  LowCardinality(String),
    os_family       LowCardinality(String),
    device_class    LowCardinality(String),
    is_bot          Bool,
    bot_reason      LowCardinality(String),
    is_synthetic    Bool,
    properties      String,
    article_id      UInt64 MATERIALIZED toUInt64OrZero(JSONExtractString(properties, 'article_id')),
    person_id       String MATERIALIZED if(user_id != 0, concat('u:', toString(user_id)), concat('d:', device_id))
) ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMMDD(derived_at, 'Asia/Shanghai')
ORDER BY (event_name, toDate(derived_at, 'Asia/Shanghai'), article_id, person_id, event_id)
TTL toDateTime(derived_at) + INTERVAL 90 DAY DELETE
SETTINGS non_replicated_deduplication_window = 1000, ttl_only_drop_parts = 1;
