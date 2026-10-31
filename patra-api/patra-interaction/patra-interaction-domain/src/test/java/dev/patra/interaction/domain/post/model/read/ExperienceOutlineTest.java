package dev.patra.interaction.domain.post.model.read;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import org.junit.jupiter.api.Test;

/// 经验的结果进外送事件时两个枚举要转常量名（PAP-141），缺了在创建时就拒绝。
class ExperienceOutlineTest {

  /// 首轮结果、最终结果必填。
  @Test
  void should_reject_missing_decisions() {
    assertThatThrownBy(() -> ExperienceOutline.of(null, FinalOutcome.IN_PROGRESS, 10, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("firstDecision");
    assertThatThrownBy(() -> ExperienceOutline.of(FirstDecision.MAJOR_REVISION, null, 10, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("finalOutcome");
  }
}
