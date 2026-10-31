package dev.patra.interaction.api.post.dto;

import lombok.Builder;

/// 一条投稿经验的结果。首轮是 `DESK_REJECT` / `REJECT_AFTER_REVIEW` / `ACCEPT` 时首轮即终局；「审稿 N 周」由调用方
/// 按天数算（展示规则）。枚举都是字符串，以后可能加值。
///
/// @param postId 帖子 ID
/// @param firstDecision 首轮决定：`DESK_REJECT`、`REJECT_AFTER_REVIEW`、`MAJOR_REVISION`、
///     `MINOR_REVISION`、`ACCEPT`
/// @param finalOutcome 最终结果：`ACCEPTED` / `REJECTED` / `WITHDRAWN` / `IN_PROGRESS`
/// @param firstRoundDays 首轮用时（天）
/// @param totalDays 投稿到最终决定的天数；修改类还没有最终日期时为空
@Builder
public record ExperienceOutlineResp(
    Long postId,
    String firstDecision,
    String finalOutcome,
    int firstRoundDays,
    Integer totalDays) {}
