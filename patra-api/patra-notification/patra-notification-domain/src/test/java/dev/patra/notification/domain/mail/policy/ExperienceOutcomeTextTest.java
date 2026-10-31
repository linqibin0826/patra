package dev.patra.notification.domain.mail.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// 投稿经验的结果段短写法（spec 8.3，同画布）。
class ExperienceOutcomeTextTest {

  /// 修改类：首轮决定 → 最终结果。
  @Test
  void revision_then_outcome() {
    assertThat(ExperienceOutcomeText.of("MAJOR_REVISION", "ACCEPTED")).contains("大修 → 录用");
    assertThat(ExperienceOutcomeText.of("MINOR_REVISION", "IN_PROGRESS")).contains("小修 → 进行中");
  }

  /// 首轮即终局只写首轮决定。
  @Test
  void terminal_first_decision_only() {
    assertThat(ExperienceOutcomeText.of("DESK_REJECT", "REJECTED")).contains("直接拒稿");
    assertThat(ExperienceOutcomeText.of("REJECT_AFTER_REVIEW", "REJECTED")).contains("审后拒稿");
    assertThat(ExperienceOutcomeText.of("ACCEPT", "ACCEPTED")).contains("录用");
  }

  /// 不认识的枚举值整段省略。
  @Test
  void unknown_values_are_empty() {
    assertThat(ExperienceOutcomeText.of("NEW_KIND", "ACCEPTED")).isEmpty();
    assertThat(ExperienceOutcomeText.of("MAJOR_REVISION", "LOST")).isEmpty();
    assertThat(ExperienceOutcomeText.of(null, null)).isEmpty();
  }
}
