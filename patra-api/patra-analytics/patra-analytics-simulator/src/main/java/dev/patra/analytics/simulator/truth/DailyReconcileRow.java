package dev.patra.analytics.simulator.truth;

import java.time.LocalDate;
import lombok.Builder;

/// 一天的对账结果，写进 ClickHouse 的 `reconcile_results`（运营分析面板 spec §6）。
///
/// @param run 运行号
/// @param day 北京时间的日期
/// @param delivered 模拟器确认送达的事件数（不含重发）
/// @param distinctEvents ClickHouse 里本运行这一天的去重事件数
/// @param viewersTruth 真值的看过人数之和
/// @param viewersCh ClickHouse 的看过人数之和
/// @param viewersAbsDiff 看过人数逐行绝对差之和
/// @param fulltextTruth 真值的读过全文人数之和
/// @param fulltextCh ClickHouse 的读过全文人数之和
/// @param fulltextAbsDiff 读过全文人数逐行绝对差之和
@Builder
public record DailyReconcileRow(
    int run,
    LocalDate day,
    long delivered,
    long distinctEvents,
    long viewersTruth,
    long viewersCh,
    long viewersAbsDiff,
    long fulltextTruth,
    long fulltextCh,
    long fulltextAbsDiff) {}
