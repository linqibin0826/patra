package dev.patra.notification.domain.kernel.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 提醒类别的解析：只认大写的名字。
class NotificationCategoryTest {

  /// 三个类别的名字都认。
  @Test
  void should_parse_exact_names() {
    assertThat(NotificationCategory.parse("REPLY")).contains(NotificationCategory.REPLY);
    assertThat(NotificationCategory.parse("MENTION")).contains(NotificationCategory.MENTION);
    assertThat(NotificationCategory.parse("HELPFUL")).contains(NotificationCategory.HELPFUL);
  }

  /// 「有帮助」只在站内，另外两类有邮件。
  @Test
  void only_helpful_has_no_email() {
    assertThat(NotificationCategory.REPLY.hasEmail()).isTrue();
    assertThat(NotificationCategory.MENTION.hasEmail()).isTrue();
    assertThat(NotificationCategory.HELPFUL.hasEmail()).isFalse();
  }

  /// 小写、不认识的、空值都不认。
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"reply", "X"})
  void should_reject_other_spellings(String raw) {
    assertThat(NotificationCategory.parse(raw)).isEmpty();
  }
}
