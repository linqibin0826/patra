-- 留存与新增（运营分析面板 spec §4.2、§4.6，ADS 层）：按首日分同期群，经典 N 日留存（恰好第 N 天又来）。
-- 新增访客 = 同期群人数。「人」先认回：未登录的设备在 device_user_links 里有对应账号的，算成那个账号。

-- 每人首日：每个原始身份（u:账号 / d:设备）在每种口径下第一次出现的日期，刷新视图每小时用最近两天的日活补进来，min 合并。
-- 判首日不必再扫 400 天的日活表（mini 上 90 天历史已要 621 MiB，攒满 400 天会超过每条查询 1 GiB 的上限）。
-- 按原始身份存、算留存时再认回：设备后来才登录时，认回跟着变。
-- 已知偏差：某人某天先被算作真人、当天晚些时候才被标为爬虫时，human 口径的首日不会撤回（量很小）。
-- 存的是 u:<账号>，注销清理要删（入库设计 §6）。
CREATE TABLE IF NOT EXISTS ads_person_first_day (
    person_id     String,
    is_synthetic  Bool,
    traffic       LowCardinality(String),
    first_day     SimpleAggregateFunction(min, Date)
) ENGINE = AggregatingMergeTree
ORDER BY (traffic, is_synthetic, person_id);

CREATE VIEW IF NOT EXISTS ads_person_first_day_calc AS
SELECT person_id, is_synthetic, traffic, min(day) AS first_day
FROM (
    SELECT day, person_id, is_synthetic, max(has_client) = 1 AND max(is_bot) = 0 AS human
    FROM daily_person_activity
    WHERE day BETWEEN {from:Date} AND {to:Date}
    GROUP BY day, person_id, is_synthetic
)
ARRAY JOIN ['human', 'all'] AS traffic
WHERE traffic = 'all' OR human
GROUP BY person_id, is_synthetic, traffic;

CREATE MATERIALIZED VIEW IF NOT EXISTS ads_person_first_day_rmv
REFRESH EVERY 1 HOUR OFFSET 10 MINUTE APPEND TO ads_person_first_day EMPTY AS
SELECT * FROM ads_person_first_day_calc(from = toDate(now(), 'Asia/Shanghai') - 1, to = toDate(now(), 'Asia/Shanghai'));

-- 同期群留存。第 N 天还没过完的同期群，dN 存 NULL（不是 0）；刷新视图每次重算最近 32 个同期群：认回会改过去的首日。
CREATE TABLE IF NOT EXISTS ads_cohort_retention (
    cohort_day    Date,
    is_synthetic  Bool,
    traffic       LowCardinality(String),
    cohort_size   UInt64,
    d1            Nullable(UInt64),
    d7            Nullable(UInt64),
    d30           Nullable(UInt64),
    computed_at   DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (cohort_day, is_synthetic, traffic);

-- 面板读这个视图：每天只取最近一次计算的结果。刷新只追加这一次算出的键，上一版有、这一版没有的键
-- （例如认回后某个同期群整组消失）FINAL 去不掉，按天取 computed_at 最大的那一版才对。
CREATE VIEW IF NOT EXISTS ads_cohort_retention_latest AS
SELECT * FROM ads_cohort_retention
WHERE (cohort_day, computed_at) IN (SELECT cohort_day, max(computed_at) FROM ads_cohort_retention GROUP BY cohort_day);

CREATE VIEW IF NOT EXISTS ads_cohort_retention_calc AS
WITH links AS (
    SELECT concat('d:', device_id) AS device_person, concat('u:', toString(argMin(user_id, first_at))) AS user_person
    FROM device_user_links
    GROUP BY device_id
),
-- 原始身份认回后的首日
mapped_firsts AS (
    SELECT if(l.user_person != '', l.user_person, f.person_id) AS person, f.is_synthetic AS is_synthetic,
           f.traffic AS traffic, f.first_day AS first_day, f.person_id AS raw_person
    FROM (
        SELECT person_id, is_synthetic, traffic, min(first_day) AS first_day
        FROM ads_person_first_day
        GROUP BY person_id, is_synthetic, traffic
    ) AS f
    LEFT JOIN links AS l ON f.person_id = l.device_person
),
-- 先挑出可能落进这批同期群的人（某个身份的首日在区间里），再看他所有身份的最早一天
candidates AS (
    SELECT DISTINCT person, is_synthetic, traffic FROM mapped_firsts WHERE first_day BETWEEN {from:Date} AND {to:Date}
),
firsts AS (
    SELECT person, is_synthetic, traffic, min(first_day) AS cohort_day, groupUniqArray(raw_person) AS raw_persons
    FROM mapped_firsts
    WHERE (person, is_synthetic, traffic) IN candidates
    GROUP BY person, is_synthetic, traffic
    HAVING cohort_day BETWEEN {from:Date} AND {to:Date}
),
-- 只读同期群成员在首日之后 30 天里的日活
member_days AS (
    SELECT f.person AS person, f.is_synthetic AS is_synthetic, f.traffic AS traffic, f.cohort_day AS cohort_day,
           a.day AS day, max(a.has_client) = 1 AND max(a.is_bot) = 0 AS human
    FROM daily_person_activity AS a
    INNER JOIN (SELECT person, is_synthetic, traffic, cohort_day, arrayJoin(raw_persons) AS raw_person FROM firsts) AS f
        ON a.person_id = f.raw_person AND a.is_synthetic = f.is_synthetic
    WHERE a.day BETWEEN {from:Date} AND {to:Date} + 30
    GROUP BY person, is_synthetic, traffic, cohort_day, day
)
SELECT cohort_day, is_synthetic, traffic,
       count() AS cohort_size,
       if(cohort_day + 1 >= toDate(now(), 'Asia/Shanghai'), NULL, sum(r[2])) AS d1,
       if(cohort_day + 7 >= toDate(now(), 'Asia/Shanghai'), NULL, sum(r[3])) AS d7,
       if(cohort_day + 30 >= toDate(now(), 'Asia/Shanghai'), NULL, sum(r[4])) AS d30,
       now64(3) AS computed_at
FROM (
    SELECT person, is_synthetic, traffic, cohort_day,
           retention(day = cohort_day, day = cohort_day + 1, day = cohort_day + 7, day = cohort_day + 30) AS r
    FROM member_days
    WHERE traffic = 'all' OR human
    GROUP BY person, is_synthetic, traffic, cohort_day
)
GROUP BY cohort_day, is_synthetic, traffic;

CREATE MATERIALIZED VIEW IF NOT EXISTS ads_cohort_retention_rmv
REFRESH EVERY 1 HOUR OFFSET 12 MINUTE APPEND TO ads_cohort_retention EMPTY AS
SELECT * FROM ads_cohort_retention_calc(from = toDate(now(), 'Asia/Shanghai') - 31, to = toDate(now(), 'Asia/Shanghai'));
