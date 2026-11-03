-- 数据质量（运营分析面板 spec §4.5，ADS 层）。
-- 迟到分布：received_at − created_at（服务端收到减客户端产生），分 7 档，负值是客户端时钟超前；另存分位数的中间状态，跨档、跨天可合并。
CREATE TABLE IF NOT EXISTS ads_daily_quality (
    day              Date,
    is_synthetic     Bool,
    source           LowCardinality(String),
    lateness_bucket  LowCardinality(String),
    events           UInt64,
    lateness_ms      AggregateFunction(quantiles(0.5, 0.95, 0.99), Int64),
    computed_at      DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (day, is_synthetic, source, lateness_bucket);

-- 面板读这个视图：每天只取最近一次计算的结果。刷新只追加这一次算出的键，上一版有、这一版没有的键
-- （例如会话后来多看了一页，从「单页」挪到「多页」）FINAL 去不掉，按天取 computed_at 最大的那一版才对。
CREATE VIEW IF NOT EXISTS ads_daily_quality_latest AS
SELECT * FROM ads_daily_quality
WHERE (day, computed_at) IN (SELECT day, max(computed_at) FROM ads_daily_quality GROUP BY day);

CREATE VIEW IF NOT EXISTS ads_daily_quality_calc AS
SELECT day, is_synthetic, source,
       multiIf(lateness < 0, 'clock_ahead', lateness < 1000, 'lt_1s', lateness < 10000, '1s_10s',
               lateness < 60000, '10s_60s', lateness < 600000, '1m_10m', lateness < 3600000, '10m_60m',
               'ge_1h') AS lateness_bucket,
       count() AS events,
       quantilesState(0.5, 0.95, 0.99)(lateness) AS lateness_ms,
       now64(3) AS computed_at
FROM (
    SELECT toDate(derived_at, 'Asia/Shanghai') AS day, is_synthetic, source,
           toInt64(toUnixTimestamp64Milli(received_at) - toUnixTimestamp64Milli(created_at)) AS lateness
    FROM events
    WHERE derived_at >= toDateTime({from:Date}, 'Asia/Shanghai')
      AND derived_at < toDateTime({to:Date} + 1, 'Asia/Shanghai')
    LIMIT 1 BY event_id
)
GROUP BY day, is_synthetic, source, lateness_bucket;

CREATE MATERIALIZED VIEW IF NOT EXISTS ads_daily_quality_rmv
REFRESH EVERY 1 HOUR OFFSET 37 MINUTE APPEND TO ads_daily_quality EMPTY AS
SELECT * FROM ads_daily_quality_calc(from = toDate(now(), 'Asia/Shanghai') - 1, to = toDate(now(), 'Asia/Shanghai'));

-- 重复率：1 − 去重后条数 ÷ 写入行数，不分合成（写入行数只能从 system.part_log 取，分不出数据种类）。
-- part_log 只留 7 天：每次刷新把写入行数快照下来；part_log 里已没有的天不输出，旧快照不会被 0 覆盖。
-- 建视图时 system.part_log 必须已存在（mini 的 config.d/patra.xml 打开了它；新起的 ClickHouse 第一次落盘系统日志后才建出来）。
CREATE TABLE IF NOT EXISTS ads_daily_duplicates (
    day              Date,
    written_rows     UInt64,
    distinct_events  UInt64,
    computed_at      DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY day;

CREATE VIEW IF NOT EXISTS ads_daily_duplicates_calc AS
SELECT w.day AS day, w.written_rows AS written_rows, d.distinct_events AS distinct_events, now64(3) AS computed_at
FROM (
    SELECT toDate(parseDateTimeBestEffort(partition_id)) AS day, sum(rows) AS written_rows
    FROM system.part_log
    WHERE database = currentDatabase() AND table = 'events' AND event_type = 'NewPart'
      AND partition_id BETWEEN toString(toYYYYMMDD({from:Date})) AND toString(toYYYYMMDD({to:Date}))
    GROUP BY partition_id
) AS w
INNER JOIN (
    SELECT toDate(derived_at, 'Asia/Shanghai') AS day, uniqExact(event_id) AS distinct_events
    FROM events
    WHERE derived_at >= toDateTime({from:Date}, 'Asia/Shanghai')
      AND derived_at < toDateTime({to:Date} + 1, 'Asia/Shanghai')
    GROUP BY day
) AS d ON w.day = d.day;

CREATE MATERIALIZED VIEW IF NOT EXISTS ads_daily_duplicates_rmv
REFRESH EVERY 1 HOUR OFFSET 42 MINUTE APPEND TO ads_daily_duplicates EMPTY AS
SELECT * FROM ads_daily_duplicates_calc(from = toDate(now(), 'Asia/Shanghai') - 1, to = toDate(now(), 'Asia/Shanghai'));
