-- 文献使用（运营分析面板 spec §4.7，ADS 层，COUNTER 5.1）：每天每篇的 Unique / Total Item Investigations 与 Requests。
-- Investigations = 查看类事件（article_viewed、fulltext_read、pdf_downloaded、article_collected），Requests = fulltext_read、pdf_downloaded。
-- Unique：按会话去重，从 article_session_reads 算（长期保留）。
-- Total：双击过滤，同一个人对同一篇两条事件相隔 ≤ 30 秒只算后一条，只能从明细算（90 天）。
CREATE TABLE IF NOT EXISTS ads_daily_counter_items (
    day                    Date,
    article_id             UInt64,
    is_synthetic           Bool,
    traffic                LowCardinality(String),
    unique_investigations  UInt64,
    unique_requests        UInt64,
    total_investigations   UInt64,
    total_requests         UInt64,
    computed_at            DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (day, is_synthetic, traffic, article_id);

-- 面板读这个视图：每天只取最近一次计算的结果。刷新只追加这一次算出的键，上一版有、这一版没有的键
-- （例如会话后来多看了一页，从「单页」挪到「多页」）FINAL 去不掉，按天取 computed_at 最大的那一版才对。
CREATE VIEW IF NOT EXISTS ads_daily_counter_items_latest AS
SELECT * FROM ads_daily_counter_items
WHERE (day, computed_at) IN (SELECT day, max(computed_at) FROM ads_daily_counter_items GROUP BY day);

CREATE VIEW IF NOT EXISTS ads_daily_counter_items_calc AS
WITH human_sessions AS (
    SELECT session_id
    FROM session_quality
    WHERE first_day BETWEEN {from:Date} - 1 AND {to:Date} + 1
    GROUP BY session_id
    HAVING max(has_client) = 1 AND max(has_bot) = 0
),
unique_counts AS (
    SELECT day, article_id, is_synthetic, traffic,
           countIf(v = 1) AS unique_investigations, countIf(ft = 1) AS unique_requests
    FROM (
        SELECT day, article_id, is_synthetic, session_id, max(viewed) AS v, max(fulltext) AS ft,
               session_id IN human_sessions AS human
        FROM article_session_reads
        WHERE day BETWEEN {from:Date} AND {to:Date}
        GROUP BY day, article_id, is_synthetic, session_id
    )
    ARRAY JOIN ['human', 'all'] AS traffic
    WHERE traffic = 'all' OR human
    GROUP BY day, article_id, is_synthetic, traffic
),
views AS (
    SELECT toDate(derived_at, 'Asia/Shanghai') AS day, article_id, is_synthetic, person_id, derived_at,
           event_name IN ('fulltext_read', 'pdf_downloaded') AS is_request,
           session_id IN human_sessions AS human
    FROM events
    WHERE event_name IN ('article_viewed', 'fulltext_read', 'pdf_downloaded', 'article_collected')
      AND article_id != 0
      AND derived_at >= toDateTime({from:Date}, 'Asia/Shanghai')
      AND derived_at < toDateTime({to:Date} + 1, 'Asia/Shanghai')
    LIMIT 1 BY event_id
),
by_traffic AS (
    SELECT day, article_id, is_synthetic, person_id, derived_at, is_request, traffic
    FROM views
    ARRAY JOIN ['human', 'all'] AS traffic
    WHERE traffic = 'all' OR human
),
-- 每条事件看同一人同一篇的下一条：没有下一条、或相隔超过 30 秒的才计数（保留后一条）
investigations AS (
    SELECT day, article_id, is_synthetic, traffic, derived_at,
           leadInFrame(toNullable(derived_at)) OVER (
               PARTITION BY traffic, person_id, article_id ORDER BY derived_at
               ROWS BETWEEN CURRENT ROW AND 1 FOLLOWING) AS next_at
    FROM by_traffic
),
requests AS (
    SELECT day, article_id, is_synthetic, traffic, derived_at,
           leadInFrame(toNullable(derived_at)) OVER (
               PARTITION BY traffic, person_id, article_id ORDER BY derived_at
               ROWS BETWEEN CURRENT ROW AND 1 FOLLOWING) AS next_at
    FROM by_traffic
    WHERE is_request
),
total_counts AS (
    SELECT day, article_id, is_synthetic, traffic, sum(i) AS total_investigations, sum(r) AS total_requests
    FROM (
        SELECT day, article_id, is_synthetic, traffic, 1 AS i, 0 AS r
        FROM investigations
        WHERE next_at IS NULL OR dateDiff('millisecond', derived_at, next_at) > 30000
        UNION ALL
        SELECT day, article_id, is_synthetic, traffic, 0 AS i, 1 AS r
        FROM requests
        WHERE next_at IS NULL OR dateDiff('millisecond', derived_at, next_at) > 30000
    )
    GROUP BY day, article_id, is_synthetic, traffic
)
SELECT day, article_id, is_synthetic, traffic,
       sum(ui) AS unique_investigations, sum(ur) AS unique_requests,
       sum(ti) AS total_investigations, sum(tr) AS total_requests,
       now64(3) AS computed_at
FROM (
    SELECT day, article_id, is_synthetic, traffic,
           unique_investigations AS ui, unique_requests AS ur, toUInt64(0) AS ti, toUInt64(0) AS tr
    FROM unique_counts
    UNION ALL
    SELECT day, article_id, is_synthetic, traffic,
           toUInt64(0) AS ui, toUInt64(0) AS ur, total_investigations AS ti, total_requests AS tr
    FROM total_counts
)
GROUP BY day, article_id, is_synthetic, traffic;

CREATE MATERIALIZED VIEW IF NOT EXISTS ads_daily_counter_items_rmv
REFRESH EVERY 1 HOUR OFFSET 32 MINUTE APPEND TO ads_daily_counter_items EMPTY AS
SELECT * FROM ads_daily_counter_items_calc(from = toDate(now(), 'Asia/Shanghai') - 1, to = toDate(now(), 'Asia/Shanghai'));
