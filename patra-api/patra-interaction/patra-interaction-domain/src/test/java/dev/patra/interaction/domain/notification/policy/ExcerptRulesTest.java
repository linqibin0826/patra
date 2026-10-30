package dev.patra.interaction.domain.notification.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.RenderedExcerpt;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// 摘录读取时的渲染：提及换成名字，再截到 120 个字素。
class ExcerptRulesTest {

  /// 主页对收件人可见：`@用户名`。
  @Test
  void should_render_visible_mention_as_username() {
    ExcerptText text = ExcerptText.builder().literal("同意 ").mention(5).build();

    RenderedExcerpt rendered =
        ExcerptRules.render(text, Map.of(5L, card(5, "chen-yu", "陈宇", true)));

    assertThat(rendered.text()).isEqualTo("同意 @chen-yu");
    assertThat(rendered.truncated()).isFalse();
  }

  /// 主页不可见：`@昵称`（同讨论区）。
  @Test
  void should_render_hidden_profile_mention_as_display_name() {
    ExcerptText text = ExcerptText.builder().mention(5).build();

    RenderedExcerpt rendered =
        ExcerptRules.render(text, Map.of(5L, card(5, "chen-yu", "陈宇", false)));

    assertThat(rendered.text()).isEqualTo("@陈宇");
  }

  /// identity 不认识的人：`@已注销用户`。
  @Test
  void should_render_unknown_user_as_deleted() {
    ExcerptText text = ExcerptText.builder().mention(5).build();

    assertThat(ExcerptRules.render(text, Map.of()).text()).isEqualTo("@已注销用户");
  }

  /// 渲染后超过 120 个字素时截断；存的时候已截断的，渲染后也标截断。
  @Test
  void should_cut_rendered_text_to_120_graphemes() {
    RenderedExcerpt rendered =
        ExcerptRules.render(ExcerptText.builder().literal("字".repeat(200)).build(), Map.of());
    assertThat(rendered.text()).isEqualTo("字".repeat(120));
    assertThat(rendered.truncated()).isTrue();

    assertThat(ExcerptRules.render(ExcerptText.of("短", true), Map.of()).truncated()).isTrue();
  }

  /// 提及不切开：`@chen-yu`（8 个字素）整个放不下就停在它前面（PAP-128 spec 第 5 节）。
  @Test
  void should_stop_before_mention_that_does_not_fit() {
    ExcerptText text = ExcerptText.builder().literal("字".repeat(117)).mention(5).build();

    RenderedExcerpt rendered =
        ExcerptRules.render(text, Map.of(5L, card(5, "chen-yu", "陈宇", true)));

    assertThat(rendered.text()).isEqualTo("字".repeat(117));
    assertThat(rendered.truncated()).isTrue();
  }

  /// 截断后去掉末尾空白（门户接着加省略号）。
  @Test
  void should_strip_trailing_space_when_cut() {
    ExcerptText text = ExcerptText.builder().literal("字".repeat(116) + " ").mention(5).build();

    RenderedExcerpt rendered =
        ExcerptRules.render(text, Map.of(5L, card(5, "chen-yu", "陈宇", true)));

    assertThat(rendered.text()).isEqualTo("字".repeat(116));
    assertThat(rendered.truncated()).isTrue();
  }

  /// 测试用的名片。
  ///
  /// @param id 用户 ID
  /// @param username 用户名
  /// @param displayName 昵称
  /// @param visible 主页是否可见
  /// @return 名片
  private static UserCard card(long id, String username, String displayName, boolean visible) {
    return UserCard.builder()
        .userId(id)
        .username(username)
        .displayName(displayName)
        .state(AccountState.ACTIVE)
        .emailVerified(true)
        .profileVisible(visible)
        .build();
  }
}
