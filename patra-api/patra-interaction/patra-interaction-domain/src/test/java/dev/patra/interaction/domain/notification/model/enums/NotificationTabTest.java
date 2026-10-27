package dev.patra.interaction.domain.notification.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.notification.exception.InvalidNotificationRequestException;
import org.junit.jupiter.api.Test;

/// 标签参数与类型归属。
class NotificationTabTest {

  /// 缺省是「回复我的」；认 `mentions`；别的 422。
  @Test
  void should_parse_tab_param() {
    assertThat(NotificationTab.fromParam(null)).isEqualTo(NotificationTab.REPLIES);
    assertThat(NotificationTab.fromParam("mentions")).isEqualTo(NotificationTab.MENTIONS);
    assertThatThrownBy(() -> NotificationTab.fromParam("all"))
        .isInstanceOf(InvalidNotificationRequestException.class);
  }

  /// 回复与经验评论归「回复我的」，@ 归「@ 我的」。
  @Test
  void should_group_types_by_tab() {
    assertThat(NotificationType.of(NotificationTab.REPLIES))
        .containsExactly(NotificationType.COMMENT_REPLY, NotificationType.POST_COMMENT);
    assertThat(NotificationType.of(NotificationTab.MENTIONS))
        .containsExactly(NotificationType.MENTION);
  }
}
