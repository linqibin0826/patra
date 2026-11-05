-- 检索（运营分析面板 spec §4.4，ADS 层）：每天的检索数、无结果数、有点击的检索数（Algolia 的检索点击率口径），以及无结果词排行。
-- traffic：human = 检索所在会话是真人会话（埋点方案 §6.5），按 session_quality 判定，会话特征看前一天到次日（与对账同理）。
-- 检索按 event_id 去重：明细表的重复行要等后台合并才消失。
CREATE TABLE IF NOT EXISTS ads_daily_search (
    day                   Date,
    is_synthetic          Bool,
    traffic               LowCardinality(String),
    mode                  LowCardinality(String),
    searches              UInt64,
    zero_result_searches  UInt64,
    clicked_searches      UInt64,
    computed_at           DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (day, is_synthetic, traffic, mode);

-- 面板读这个视图：每天只取最近一次计算的结果。刷新只追加这一次算出的键，上一版有、这一版没有的键
-- （例如会话后来多看了一页，从「单页」挪到「多页」）FINAL 去不掉，按天取 computed_at 最大的那一版才对。
CREATE VIEW IF NOT EXISTS ads_daily_search_latest AS
SELECT * FROM ads_daily_search
WHERE (day, computed_at) IN (SELECT day, max(computed_at) FROM ads_daily_search GROUP BY day);

CREATE VIEW IF NOT EXISTS ads_daily_search_calc AS
WITH human_sessions AS (
    SELECT session_id
    FROM session_quality
    WHERE first_day BETWEEN {from:Date} - 1 AND {to:Date} + 1
    GROUP BY session_id
    HAVING max(has_client) = 1 AND max(has_bot) = 0
),
searches AS (
    SELECT toDate(derived_at, 'Asia/Shanghai') AS day, is_synthetic,
           session_id IN human_sessions AS human,
           JSONExtractString(properties, 'search_id') AS search_id,
           JSONExtractString(properties, 'mode') AS mode,
           JSONExtractInt(properties, 'result_count') = 0 AS zero
    FROM events
    WHERE event_name = 'search_performed'
      AND derived_at >= toDateTime({from:Date}, 'Asia/Shanghai')
      AND derived_at < toDateTime({to:Date} + 1, 'Asia/Shanghai')
    LIMIT 1 BY event_id
),
-- 点击可能在检索之后跨过午夜，多看一天
clicked AS (
    SELECT DISTINCT JSONExtractString(properties, 'list_id') AS list_id
    FROM events
    WHERE event_name = 'article_clicked' AND JSONExtractString(properties, 'list_type') = 'search'
      AND derived_at >= toDateTime({from:Date}, 'Asia/Shanghai')
      AND derived_at < toDateTime({to:Date} + 2, 'Asia/Shanghai')
)
SELECT day, is_synthetic, traffic, mode,
       count() AS searches,
       countIf(zero) AS zero_result_searches,
       countIf(search_id IN clicked) AS clicked_searches,
       now64(3) AS computed_at
FROM searches
ARRAY JOIN ['human', 'all'] AS traffic
WHERE traffic = 'all' OR human
GROUP BY day, is_synthetic, traffic, mode;

CREATE MATERIALIZED VIEW IF NOT EXISTS ads_daily_search_rmv
REFRESH EVERY 1 HOUR OFFSET 22 MINUTE APPEND TO ads_daily_search EMPTY AS
SELECT * FROM ads_daily_search_calc(from = toDate(now(), 'Asia/Shanghai') - 1, to = toDate(now(), 'Asia/Shanghai'));

-- 无结果词：只取普通检索且检索词非空（精确定位查不到多半是号码输错）；词规范化为去首尾空白、小写、连续空白压成一个。
-- people 存 uniqExact 的中间状态，跨天合并时同一个人不重复算。检索词原样只留 90 天（埋点方案决策 6）。
CREATE TABLE IF NOT EXISTS ads_daily_zero_result_terms (
    day           Date,
    is_synthetic  Bool,
    traffic       LowCardinality(String),
    term          String,
    searches      UInt64,
    people        AggregateFunction(uniqExact, String),
    computed_at   DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (day, is_synthetic, traffic, term)
TTL day + INTERVAL 90 DAY DELETE;

-- 面板读这个视图：每天只取最近一次计算的结果。刷新只追加这一次算出的键，上一版有、这一版没有的键
-- （例如会话后来多看了一页，从「单页」挪到「多页」）FINAL 去不掉，按天取 computed_at 最大的那一版才对。
CREATE VIEW IF NOT EXISTS ads_daily_zero_result_terms_latest AS
SELECT * FROM ads_daily_zero_result_terms
WHERE (day, computed_at) IN (SELECT day, max(computed_at) FROM ads_daily_zero_result_terms GROUP BY day);

CREATE VIEW IF NOT EXISTS ads_daily_zero_result_terms_calc AS
WITH human_sessions AS (
    SELECT session_id
    FROM session_quality
    WHERE first_day BETWEEN {from:Date} - 1 AND {to:Date} + 1
    GROUP BY session_id
    HAVING max(has_client) = 1 AND max(has_bot) = 0
),
zero_searches AS (
    SELECT toDate(derived_at, 'Asia/Shanghai') AS day, is_synthetic, person_id,
           session_id IN human_sessions AS human,
           replaceRegexpAll(lower(trimBoth(JSONExtractString(properties, 'query'))), '\\s+', ' ') AS term
    FROM events
    WHERE event_name = 'search_performed'
      AND JSONExtractString(properties, 'mode') = 'keyword'
      AND JSONExtractInt(properties, 'result_count') = 0
      AND derived_at >= toDateTime({from:Date}, 'Asia/Shanghai')
      AND derived_at < toDateTime({to:Date} + 1, 'Asia/Shanghai')
    LIMIT 1 BY event_id
)
SELECT day, is_synthetic, traffic, term,
       count() AS searches,
       uniqExactState(person_id) AS people,
       now64(3) AS computed_at
FROM zero_searches
ARRAY JOIN ['human', 'all'] AS traffic
WHERE term != '' AND (traffic = 'all' OR human)
GROUP BY day, is_synthetic, traffic, term;

CREATE MATERIALIZED VIEW IF NOT EXISTS ads_daily_zero_result_terms_rmv
REFRESH EVERY 1 HOUR OFFSET 27 MINUTE APPEND TO ads_daily_zero_result_terms EMPTY AS
SELECT * FROM ads_daily_zero_result_terms_calc(from = toDate(now(), 'Asia/Shanghai') - 1, to = toDate(now(), 'Asia/Shanghai'));
