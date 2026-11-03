-- 模拟器的对账结果（运营分析面板 spec §6）：模拟器的 reconcile 命令按天写入，面板据此画丢失率与对账误差。
-- 丢失率 = 1 − distinct_events ÷ delivered；同一运行、同一天重跑时以最新一次为准。
CREATE TABLE IF NOT EXISTS reconcile_results (
    run                UInt16,
    day                Date,
    delivered          UInt64,
    distinct_events    UInt64,
    viewers_truth      UInt64,
    viewers_ch         UInt64,
    viewers_abs_diff   UInt64,
    fulltext_truth     UInt64,
    fulltext_ch        UInt64,
    fulltext_abs_diff  UInt64,
    checked_at         DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(checked_at)
ORDER BY (run, day);
