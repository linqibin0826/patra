-- 漏斗、跳出与会话构成（运营分析面板 spec §4.3，ADS 层）：一个会话聚合出一行，再按会话首日（北京时间）汇总。
-- funnel_level：没有 page_viewed 记 0；有的记 1 + windowFunnel 走到的步数。第 1 步「访问」是会话级门槛、不排序：
--   search_performed 在服务端渲染时发，page_viewed 要等浏览器渲染完，落在检索页的会话里检索总比访问早。
-- session_kind 三类互斥：bot（有一条爬虫事件）> human（有浏览器事件）> no_script；会话数可以相加。
CREATE TABLE IF NOT EXISTS ads_daily_sessions (
    day           Date,
    is_synthetic  Bool,
    session_kind  LowCardinality(String),
    funnel_level  UInt8,
    single_page   Bool,
    sessions      UInt64,
    computed_at   DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (day, is_synthetic, session_kind, funnel_level, single_page);

-- 面板读这个视图：每天只取最近一次计算的结果。刷新只追加这一次算出的键，上一版有、这一版没有的键
-- （例如会话后来多看了一页，从「单页」挪到「多页」）FINAL 去不掉，按天取 computed_at 最大的那一版才对。
CREATE VIEW IF NOT EXISTS ads_daily_sessions_latest AS
SELECT * FROM ads_daily_sessions
WHERE (day, computed_at) IN (SELECT day, max(computed_at) FROM ads_daily_sessions GROUP BY day);

CREATE VIEW IF NOT EXISTS ads_daily_sessions_calc AS
SELECT day, is_synthetic, session_kind, funnel_level, single_page, count() AS sessions, now64(3) AS computed_at
FROM (
    SELECT session_id,
           toDate(min(derived_at), 'Asia/Shanghai') AS day,
           any(is_synthetic) AS is_synthetic,
           multiIf(max(is_bot), 'bot', max(source = 'client'), 'human', 'no_script') AS session_kind,
           countIf(event_name = 'page_viewed') AS page_views,
           page_views = 1 AS single_page,
           toUInt8(if(page_views = 0, 0,
               1 + windowFunnel(1800)(toDateTime(derived_at),
                   event_name = 'search_performed',
                   event_name = 'article_clicked' AND JSONExtractString(properties, 'list_type') = 'search',
                   event_name = 'article_viewed',
                   event_name IN ('fulltext_read', 'pdf_downloaded')))) AS funnel_level
    FROM events
    -- 前后各多读一天：跨午夜的会话要看到整个会话才能定它的首日，后半段不能在次日被当成新会话
    WHERE derived_at >= toDateTime({from:Date} - 1, 'Asia/Shanghai')
      AND derived_at < toDateTime({to:Date} + 2, 'Asia/Shanghai')
    GROUP BY session_id
)
WHERE day BETWEEN {from:Date} AND {to:Date}
GROUP BY day, is_synthetic, session_kind, funnel_level, single_page;

CREATE MATERIALIZED VIEW IF NOT EXISTS ads_daily_sessions_rmv
REFRESH EVERY 1 HOUR OFFSET 17 MINUTE APPEND TO ads_daily_sessions EMPTY AS
SELECT * FROM ads_daily_sessions_calc(from = toDate(now(), 'Asia/Shanghai') - 1, to = toDate(now(), 'Asia/Shanghai'));
