package dev.patra.notification.domain.mail.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import org.junit.jupiter.api.Test;

/// 逐条邮件的退订类别跟着提醒类别走（PAP-142 第 7 节）。
class UnsubscribeLinkCategoryTest {

  /// 回复与 @ 各对一类；有帮助没有邮件。
  @Test
  void maps_reply_and_mention_and_rejects_helpful() {
    assertThat(UnsubscribeLinkCategory.of(NotificationCategory.REPLY))
        .isEqualTo(UnsubscribeLinkCategory.REPLY);
    assertThat(UnsubscribeLinkCategory.of(NotificationCategory.MENTION))
        .isEqualTo(UnsubscribeLinkCategory.MENTION);
    assertThatThrownBy(() -> UnsubscribeLinkCategory.of(NotificationCategory.HELPFUL))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
