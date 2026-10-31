package dev.patra.interaction.api.reaction.event;

/// 投稿经验的结果，只出现在「有帮助已点」里、目标是经验时（PAP-141）：经验没写正文时，提醒条目的摘录行写结果行
/// （`大修 → 录用 · 审稿 6 周`）。「审稿 N 周」由消费方按天数算。
///
/// @param firstDecision 首轮结果：`DESK_REJECT`、`REJECT_AFTER_REVIEW`、`MAJOR_REVISION`、`MINOR_REVISION`、
///     `ACCEPT`（前两个与最后一个是首轮即终局）
/// @param finalOutcome 最终结果：`ACCEPTED`、`REJECTED`、`WITHDRAWN`、`IN_PROGRESS`
/// @param firstRoundDays 首轮用时（天）
/// @param totalDays 投稿到最终决定的天数；首轮即终局时等于首轮用时；修改类没填最终日期为 `null`
public record ExperienceOutcome(
    String firstDecision, String finalOutcome, int firstRoundDays, Integer totalDays) {

  /// 创建。
  ///
  /// @param firstDecision 首轮结果
  /// @param finalOutcome 最终结果
  /// @param firstRoundDays 首轮用时
  /// @param totalDays 总用时，可空
  /// @return 结果
  public static ExperienceOutcome of(
      String firstDecision, String finalOutcome, int firstRoundDays, Integer totalDays) {
    return new ExperienceOutcome(firstDecision, finalOutcome, firstRoundDays, totalDays);
  }
}
