package dev.patra.interaction.domain.reaction.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.reaction.exception.HelpfulTargetNotFoundException;
import dev.patra.interaction.domain.reaction.exception.SelfHelpfulException;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import org.junit.jupiter.api.Test;

/// 目标画像：谁的获赞、能不能点（先判可点，再判是不是自己）。
class TargetProfileTest {

  private static final HelpfulTarget COMMENT = HelpfulTarget.of(HelpfulTargetType.COMMENT, 42L);
  private static final long AUTHOR = 7L;
  private static final long OTHER = 8L;

  @Test
  void should_credit_author_when_not_anonymous() {
    assertThat(TargetProfile.of(COMMENT, AUTHOR, true, false).creditedUserId()).isEqualTo(AUTHOR);
  }

  @Test
  void should_credit_nobody_when_anonymous() {
    assertThat(TargetProfile.of(COMMENT, AUTHOR, true, true).creditedUserId()).isNull();
  }

  @Test
  void should_let_others_mark_available_target() {
    assertThatCode(() -> TargetProfile.of(COMMENT, AUTHOR, true, true).requireMarkableBy(OTHER))
        .doesNotThrowAnyException();
  }

  @Test
  void should_reject_unavailable_target() {
    assertThatThrownBy(
            () -> TargetProfile.of(COMMENT, AUTHOR, false, false).requireMarkableBy(OTHER))
        .isInstanceOf(HelpfulTargetNotFoundException.class);
    assertThatThrownBy(() -> TargetProfile.of(COMMENT, AUTHOR, false, false).requireAvailable())
        .isInstanceOf(HelpfulTargetNotFoundException.class);
  }

  /// 匿名经验的作者给自己点同样被拦：判的是作者，不是受益人。
  @Test
  void should_reject_author_even_when_anonymous() {
    assertThatThrownBy(
            () -> TargetProfile.of(COMMENT, AUTHOR, true, true).requireMarkableBy(AUTHOR))
        .isInstanceOf(SelfHelpfulException.class);
  }

  /// 已删除又是自己的：先报已删除（门户此时本就没有按钮）。
  @Test
  void should_report_not_found_before_self() {
    assertThatThrownBy(
            () -> TargetProfile.of(COMMENT, AUTHOR, false, false).requireMarkableBy(AUTHOR))
        .isInstanceOf(HelpfulTargetNotFoundException.class);
  }
}
