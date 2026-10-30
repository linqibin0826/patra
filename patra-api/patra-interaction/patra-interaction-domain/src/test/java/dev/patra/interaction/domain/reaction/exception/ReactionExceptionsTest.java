package dev.patra.interaction.domain.reaction.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.core.error.trait.StandardErrorTrait;
import org.junit.jupiter.api.Test;

/// 有帮助的异常：标准特征定 HTTP 状态，原因码给门户选文案。
class ReactionExceptionsTest {

  @Test
  void should_report_missing_target_as_not_found() {
    HelpfulTargetNotFoundException ex = new HelpfulTargetNotFoundException();

    assertThat(ex.getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.NOT_FOUND, ReactionErrorTrait.HELPFUL_TARGET_NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("这条内容不存在或已删除，不能点有帮助");
  }

  @Test
  void should_report_self_as_forbidden() {
    SelfHelpfulException ex = new SelfHelpfulException();

    assertThat(ex.getErrorTraits())
        .containsExactlyInAnyOrder(StandardErrorTrait.FORBIDDEN, ReactionErrorTrait.SELF_HELPFUL);
    assertThat(ex.getMessage()).isEqualTo("不能给自己的内容点有帮助");
  }
}
