package dev.patra.notification.domain.mail.policy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// 投稿经验结果段的短写法（spec 8.3，同画布）：首轮即终局时只写首轮决定（`直接拒稿`），否则写
/// `首轮决定 → 最终结果`（`大修 → 录用`）。中文名照门户 `lib/experience/labels.ts`。
public final class ExperienceOutcomeText {

  /// 首轮决定的中文名。
  private static final Map<String, String> FIRST_DECISIONS =
      Map.of(
          "DESK_REJECT", "直接拒稿",
          "REJECT_AFTER_REVIEW", "审后拒稿",
          "MAJOR_REVISION", "大修",
          "MINOR_REVISION", "小修",
          "ACCEPT", "录用");

  /// 最终结果的中文名。
  private static final Map<String, String> FINAL_OUTCOMES =
      Map.of("ACCEPTED", "录用", "REJECTED", "拒稿", "WITHDRAWN", "撤稿", "IN_PROGRESS", "进行中");

  /// 首轮就是最终结果的三种。
  private static final Set<String> TERMINAL =
      Set.of("DESK_REJECT", "REJECT_AFTER_REVIEW", "ACCEPT");

  /// 只有静态方法，不实例化。
  private ExperienceOutcomeText() {}

  /// 结果段。
  ///
  /// @param firstDecision 首轮决定的枚举名，可空
  /// @param finalOutcome 最终结果的枚举名，可空
  /// @return 文字；有不认识的值时为空（整段省略）
  public static Optional<String> of(String firstDecision, String finalOutcome) {
    String first = firstDecision == null ? null : FIRST_DECISIONS.get(firstDecision);
    if (first == null) {
      return Optional.empty();
    }
    if (TERMINAL.contains(firstDecision)) {
      return Optional.of(first);
    }
    String last = finalOutcome == null ? null : FINAL_OUTCOMES.get(finalOutcome);
    return last == null ? Optional.empty() : Optional.of(first + " → " + last);
  }
}
