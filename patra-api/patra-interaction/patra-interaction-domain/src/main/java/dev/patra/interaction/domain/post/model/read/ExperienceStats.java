package dev.patra.interaction.domain.post.model.read;

import lombok.Builder;

/// PG 在读取时算出的一个期刊的原始统计（spec 第 7 节）：窗口内的计数与 `percentile_cont` 的原值（未取整）。
///
/// @param allCount 全部条数（不看窗口）
/// @param windowCount 窗口内条数
/// @param firstRoundCount 窗口内非直接拒稿的条数（首轮用时样本）
/// @param firstRoundP25 首轮用时 P25，样本为 0 时 `null`
/// @param firstRoundMedian 首轮用时中位数
/// @param firstRoundP75 首轮用时 P75
/// @param deskRejectCount 窗口内直接拒稿的条数
/// @param deskRejectMedian 直接拒稿用时中位数，样本为 0 时 `null`
/// @param accepted 最终录用
/// @param rejected 最终拒稿
/// @param withdrawn 撤稿
/// @param inProgress 进行中
@Builder
public record ExperienceStats(
    long allCount,
    long windowCount,
    long firstRoundCount,
    Double firstRoundP25,
    Double firstRoundMedian,
    Double firstRoundP75,
    long deskRejectCount,
    Double deskRejectMedian,
    long accepted,
    long rejected,
    long withdrawn,
    long inProgress) {}
