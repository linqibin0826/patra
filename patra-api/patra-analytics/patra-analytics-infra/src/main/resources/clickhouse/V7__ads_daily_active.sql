-- 活跃（运营分析面板 spec §4.1，ADS 层）：每天的 DAU / WAU / MAU，按滚动窗口（含当天往前 7 / 30 天）。
-- traffic：human = 当天有浏览器事件且无爬虫事件的人（入库设计 §5.5），all = 全部；合成与真实的人不重叠，按 is_synthetic 分行。
-- 每张 ADS 表 = 计算视图 *_calc(from, to) + 目标表 + 每小时的刷新视图；读时 FINAL 取最新一版（spec §3.2）。
CREATE TABLE IF NOT EXISTS ads_daily_active (
    day           Date,
    is_synthetic  Bool,
    traffic       LowCardinality(String),
    dau           UInt64,
    wau           UInt64,
    mau           UInt64,
    computed_at   DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (day, is_synthetic, traffic);

-- 面板读这个视图：每天只取最近一次计算的结果。刷新只追加这一次算出的键，上一版有、这一版没有的键
-- （例如会话后来多看了一页，从「单页」挪到「多页」）FINAL 去不掉，按天取 computed_at 最大的那一版才对。
CREATE VIEW IF NOT EXISTS ads_daily_active_latest AS
SELECT * FROM ads_daily_active
WHERE (day, computed_at) IN (SELECT day, max(computed_at) FROM ads_daily_active GROUP BY day);

CREATE VIEW IF NOT EXISTS ads_daily_active_calc AS
WITH person_days AS (
    SELECT day, person_id, is_synthetic, max(has_client) = 1 AND max(is_bot) = 0 AS human
    FROM daily_person_activity
    WHERE day BETWEEN {from:Date} - 29 AND {to:Date}
    GROUP BY day, person_id, is_synthetic
),
day_states AS (
    SELECT day, is_synthetic, traffic, uniqExactState(person_id) AS people
    FROM person_days
    ARRAY JOIN ['human', 'all'] AS traffic
    WHERE traffic = 'all' OR human
    GROUP BY day, is_synthetic, traffic
)
SELECT day, is_synthetic, traffic, dau, wau, mau, now64(3) AS computed_at
FROM (
    SELECT day, is_synthetic, traffic,
           finalizeAggregation(people) AS dau,
           uniqExactMerge(people) OVER (PARTITION BY is_synthetic, traffic ORDER BY toRelativeDayNum(day)
                                        RANGE BETWEEN 6 PRECEDING AND CURRENT ROW) AS wau,
           uniqExactMerge(people) OVER (PARTITION BY is_synthetic, traffic ORDER BY toRelativeDayNum(day)
                                        RANGE BETWEEN 29 PRECEDING AND CURRENT ROW) AS mau
    FROM day_states
)
WHERE day BETWEEN {from:Date} AND {to:Date};

-- EMPTY：建好时不立刻算，按时刻表跑；各表错开分钟，避开整点（热读每小时整点重算）
CREATE MATERIALIZED VIEW IF NOT EXISTS ads_daily_active_rmv
REFRESH EVERY 1 HOUR OFFSET 7 MINUTE APPEND TO ads_daily_active EMPTY AS
SELECT * FROM ads_daily_active_calc(from = toDate(now(), 'Asia/Shanghai') - 1, to = toDate(now(), 'Asia/Shanghai'));
