package dev.patra.notification.domain.inbox.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import org.junit.jupiter.api.Test;

/// 有帮助的目标种类：只认 interaction 发来的两个大写名字，各对应一个「有帮助」类型。
class HelpfulTargetTypeTest {

  /// 认识的两个名字。
  @Test
  void should_parse_known_names() {
    assertThat(HelpfulTargetType.parse("COMMENT")).contains(HelpfulTargetType.COMMENT);
    assertThat(HelpfulTargetType.parse("POST")).contains(HelpfulTargetType.POST);
  }

  /// 小写、别的种类、空都不认（不抛异常，由处理器确认并跳过）。
  @Test
  void should_not_parse_unknown_names() {
    assertThat(HelpfulTargetType.parse("comment")).isEmpty();
    assertThat(HelpfulTargetType.parse("PUBLICATION")).isEmpty();
    assertThat(HelpfulTargetType.parse(null)).isEmpty();
  }

  /// 评论 → 「评论有帮助」，经验 → 「经验有帮助」，都归「有帮助」标签。
  @Test
  void should_map_to_helpful_notification_types() {
    assertThat(HelpfulTargetType.COMMENT.notificationType())
        .isEqualTo(NotificationType.COMMENT_HELPFUL);
    assertThat(HelpfulTargetType.POST.notificationType()).isEqualTo(NotificationType.POST_HELPFUL);
    assertThat(NotificationType.of(NotificationCategory.HELPFUL))
        .containsExactly(NotificationType.COMMENT_HELPFUL, NotificationType.POST_HELPFUL);
  }
}
