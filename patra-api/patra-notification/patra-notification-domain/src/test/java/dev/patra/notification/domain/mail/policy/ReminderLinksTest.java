package dev.patra.notification.domain.mail.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.enums.UnsubscribeLinkCategory;
import org.junit.jupiter.api.Test;

/// 提醒邮件里的门户链接（spec 8.3、PAP-142 第 7 节）。
class ReminderLinksTest {

  private static final ReminderLinks LINKS = ReminderLinks.of("https://patra.linqibin.dev/");
  private static final String TOKEN = "T".repeat(43);

  /// 门户地址末尾的 `/` 去掉。
  @Test
  void trailing_slash_is_removed() {
    assertThat(LINKS.settings()).isEqualTo("https://patra.linqibin.dev/settings/notifications");
  }

  /// 两个退订地址照 PAP-142 的长期契约。
  @Test
  void unsubscribe_links_match_contract() {
    assertThat(LINKS.unsubscribeConfirm(TOKEN, UnsubscribeLinkCategory.MENTION))
        .isEqualTo("https://patra.linqibin.dev/unsubscribe#token=" + TOKEN + "&category=MENTION");
    assertThat(LINKS.unsubscribeOneClick(TOKEN, UnsubscribeLinkCategory.DIGEST))
        .isEqualTo(
            "https://patra.linqibin.dev/unsubscribe/one-click?token=" + TOKEN + "&category=DIGEST");
  }

  /// 评论永久链接：文献与经验各一种（同门户 `notificationHref`）。
  @Test
  void comment_permalinks() {
    assertThat(LINKS.comment(true, "18", 7L))
        .isEqualTo("https://patra.linqibin.dev/papers/18?comment=7");
    assertThat(LINKS.comment(false, "32", 9L))
        .isEqualTo("https://patra.linqibin.dev/posts/32?comment=9");
  }

  /// 提醒页的两个标签。
  @Test
  void inbox_tabs() {
    assertThat(LINKS.inbox(NotificationCategory.REPLY))
        .isEqualTo("https://patra.linqibin.dev/notifications");
    assertThat(LINKS.inbox(NotificationCategory.MENTION))
        .isEqualTo("https://patra.linqibin.dev/notifications/mentions");
    assertThat(LINKS.inboxHome()).isEqualTo("https://patra.linqibin.dev/notifications");
  }
}
