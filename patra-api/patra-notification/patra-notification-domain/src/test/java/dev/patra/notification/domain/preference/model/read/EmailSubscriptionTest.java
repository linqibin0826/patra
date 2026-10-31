package dev.patra.notification.domain.preference.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import org.junit.jupiter.api.Test;

/// 给 mail 的邮件订阅：按类别取档位，有帮助恒为关；日志里看不到令牌。
class EmailSubscriptionTest {

  private static final String TOKEN = "T".repeat(43);

  /// 按类别取档位。
  @Test
  void mode_of_returns_each_category() {
    EmailSubscription subscription =
        EmailSubscription.of(7L, EmailMode.DIGEST, EmailMode.OFF, TOKEN);

    assertThat(subscription.modeOf(NotificationCategory.REPLY)).isEqualTo(EmailMode.DIGEST);
    assertThat(subscription.modeOf(NotificationCategory.MENTION)).isEqualTo(EmailMode.OFF);
    assertThat(subscription.modeOf(NotificationCategory.HELPFUL)).isEqualTo(EmailMode.OFF);
  }

  /// `toString()` 不含令牌原文。
  @Test
  void to_string_hides_token() {
    EmailSubscription subscription =
        EmailSubscription.of(7L, EmailMode.INSTANT, EmailMode.INSTANT, TOKEN);

    assertThat(subscription.toString()).doesNotContain(TOKEN).contains("***");
  }
}
