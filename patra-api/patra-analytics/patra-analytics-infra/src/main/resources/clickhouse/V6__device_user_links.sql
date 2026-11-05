-- 设备与账号的对应（运营分析面板 spec §4.6）：登录后的事件同时带设备与用户，据此把登录前的设备认回账号，只用于新增与留存。
-- 每对（设备，用户）一行，记第一次一起出现的时刻；查询时每台设备取时刻最早的用户：argMin(user_id, first_at)。
-- 按对存而不是每台设备一行：注销清理能直接按 user_id 删（入库设计 §6）。
CREATE TABLE IF NOT EXISTS device_user_links (
    device_id     String,
    user_id       UInt64,
    first_at      SimpleAggregateFunction(min, DateTime64(3, 'UTC'))
) ENGINE = AggregatingMergeTree
ORDER BY (device_id, user_id)
SETTINGS non_replicated_deduplication_window = 1000;

CREATE MATERIALIZED VIEW IF NOT EXISTS device_user_links_mv TO device_user_links AS
SELECT device_id, user_id, derived_at AS first_at
FROM events
WHERE user_id != 0;

-- 回填用：按北京日期区间从明细补历史，输出与目标表同列
CREATE VIEW IF NOT EXISTS device_user_links_calc AS
SELECT device_id, user_id, min(derived_at) AS first_at
FROM events
WHERE toDate(derived_at, 'Asia/Shanghai') BETWEEN {from:Date} AND {to:Date}
  AND user_id != 0
GROUP BY device_id, user_id;
