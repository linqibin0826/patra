package dev.patra.notification.domain.kernel.model.read;

/// 投稿经验的结果，按 interaction 给的原样（枚举名字符串与天数），只透传给门户与邮件模板，不在这里解释。
///
/// @param firstDecision 首轮决定
/// @param finalOutcome 最终结果
/// @param firstRoundDays 首轮用时（天）
/// @param totalDays 投稿到最终决定的天数，可空
public record ExperienceOutline(
    String firstDecision, String finalOutcome, int firstRoundDays, Integer totalDays) {

  /// 构造。
  ///
  /// @param firstDecision 首轮决定
  /// @param finalOutcome 最终结果
  /// @param firstRoundDays 首轮用时（天）
  /// @param totalDays 总天数，可空
  /// @return 结果
  public static ExperienceOutline of(
      String firstDecision, String finalOutcome, int firstRoundDays, Integer totalDays) {
    return new ExperienceOutline(firstDecision, finalOutcome, firstRoundDays, totalDays);
  }
}
