package dev.patra.notification.domain.inbox.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.inbox.exception.InvalidInboxRequestException;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import org.junit.jupiter.api.Test;

/// 列表的三个标签与查询参数的写法。
class InboxTabTest {

  /// 三个写法各对一个类别；缺省是「回复我的」。
  @Test
  void should_parse_params() {
    assertThat(InboxTab.fromParam("replies").category()).isEqualTo(NotificationCategory.REPLY);
    assertThat(InboxTab.fromParam("mentions").category()).isEqualTo(NotificationCategory.MENTION);
    assertThat(InboxTab.fromParam("helpful").category()).isEqualTo(NotificationCategory.HELPFUL);
    assertThat(InboxTab.fromParam(null)).isEqualTo(InboxTab.REPLIES);
    assertThat(InboxTab.fromParam(" ")).isEqualTo(InboxTab.REPLIES);
  }

  /// 不认识的写法 422。
  @Test
  void should_reject_unknown_param() {
    assertThatThrownBy(() -> InboxTab.fromParam("likes"))
        .isInstanceOf(InvalidInboxRequestException.class);
  }

  /// 动作类型各归一个类别。
  @Test
  void should_map_types_to_categories() {
    assertThat(NotificationType.of(NotificationCategory.REPLY))
        .containsExactly(NotificationType.COMMENT_REPLY, NotificationType.POST_COMMENT);
    assertThat(NotificationType.MENTION.category()).isEqualTo(NotificationCategory.MENTION);
  }
}
