package dev.patra.notification.adapter.inbox.rest.response;

import dev.patra.notification.domain.kernel.model.read.ExperienceOutline;

/// 投稿经验的结果（门户拼「大修 → 录用 · 审稿 6 周」用；周数由门户按天数算）。
///
/// @param firstDecision 首轮决定（枚举名）
/// @param finalOutcome 最终结果（枚举名）
/// @param firstRoundDays 首轮用时（天）
/// @param totalDays 投稿到最终决定的天数，可空
public record ExperienceResponse(
    String firstDecision, String finalOutcome, int firstRoundDays, Integer totalDays) {

  /// 转换。
  ///
  /// @param outline 结果，可空
  /// @return 响应，可空
  public static ExperienceResponse from(ExperienceOutline outline) {
    if (outline == null) {
      return null;
    }
    return new ExperienceResponse(
        outline.firstDecision(),
        outline.finalOutcome(),
        outline.firstRoundDays(),
        outline.totalDays());
  }
}
