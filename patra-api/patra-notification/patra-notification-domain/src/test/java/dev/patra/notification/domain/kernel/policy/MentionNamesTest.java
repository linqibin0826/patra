package dev.patra.notification.domain.kernel.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import org.junit.jupiter.api.Test;

/// 摘录里 @ 到的人显示什么名字（PAP-128 交接第 1 条）。
class MentionNamesTest {

  /// 主页对收件人可见：用户名。
  @Test
  void should_use_username_when_profile_visible() {
    assertThat(MentionNames.of(card("zhou", "周敏", true))).isEqualTo("zhou");
  }

  /// 主页不可见：昵称，不暴露用户名。
  @Test
  void should_use_display_name_when_profile_hidden() {
    assertThat(MentionNames.of(card("zhou", "周敏", false))).isEqualTo("周敏");
  }

  /// identity 不认识（没有名片），或已注销的占位（名字都为空）：写「已注销用户」。
  @Test
  void should_name_unknown_or_deleted_user() {
    assertThat(MentionNames.of(null)).isEqualTo(MentionNames.DELETED_USER);
    assertThat(MentionNames.of(card(null, null, false))).isEqualTo("已注销用户");
  }

  /// 测试用名片。
  ///
  /// @param username 用户名
  /// @param displayName 昵称
  /// @param visible 主页是否可见
  /// @return 名片
  private static UserCard card(String username, String displayName, boolean visible) {
    return UserCard.builder()
        .userId(1L)
        .username(username)
        .displayName(displayName)
        .state(AccountState.ACTIVE)
        .profileVisible(visible)
        .build();
  }
}
