package dev.patra.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.app.mail.usecase.send.SendResult;
import dev.patra.notification.domain.mail.model.enums.MailDeliveryFailure;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// 离站逐条的提醒邮件端到端（PAP-146 AC，spec 第 11 节）：评论事件 → 信箱 → 排队 → 到点 → 装配 → SMTP（GreenMail）。
@DisplayName("离站逐条提醒邮件端到端")
class ReminderMailFlowIT extends ReminderMailITSupport {

  /// AC：不在站上时，回复后 10～12 分钟内发出。9 分 59 秒时没有信，10 分钟时有一封，带摘录与直达那一楼的链接。
  @Test
  void instant_reply_is_sent_after_ten_minutes() throws Exception {
    reply(500L, "不过作者自己也写了需要更多研究来证实");

    advance(Duration.ofMinutes(10).minusSeconds(1));
    runReminderJob();
    assertThat(mails()).isEmpty();

    advance(Duration.ofSeconds(1));
    runReminderJob();

    assertThat(mails()).hasSize(1);
    MimeMessage mail = mails()[0];
    assertThat(mail.getSubject()).isEqualTo("王立 回复了你的评论");
    assertThat(mail.getAllRecipients()[0].toString()).isEqualTo(MAILBOX);
    String html = html(mail);
    assertThat(html)
        .contains("不过作者自己也写了需要更多研究来证实")
        .contains("href=\"http://portal.test/papers/18?comment=500\"")
        .contains("查看并回复 →");
  }

  /// AC：到点时已读，不发。
  @Test
  void read_before_due_is_not_sent() {
    reply(500L, "好");
    advance(Duration.ofMinutes(3));
    readAll();

    advance(Duration.ofMinutes(7));
    runReminderJob();

    assertThat(mails()).isEmpty();
    assertThat(queueRows()).isZero();
  }

  /// AC：这 10 分钟里在站上（拉过未读数），不发。
  @Test
  void seen_during_wait_is_not_sent() {
    reply(500L, "好");
    advance(Duration.ofMinutes(5));
    pollUnreadCount();

    advance(Duration.ofMinutes(5));
    runReminderJob();

    assertThat(mails()).isEmpty();
  }

  /// AC：同一时刻到点的回复与 @ 分成两封。
  @Test
  void reply_and_mention_due_together_are_two_mails() throws Exception {
    reply(500L, "回复你");
    mention(501L, "<@2> 你看这个");

    advance(Duration.ofMinutes(10));
    runReminderJob();

    assertThat(mails())
        .extracting(MimeMessage::getSubject)
        .containsExactlyInAnyOrder("王立 回复了你的评论", "王立 在评论里 @ 了你");
  }

  /// 合并窗口：第一条到点时，同一类后来的也一起发，一封两条。
  @Test
  void later_reply_in_window_joins_the_first() throws Exception {
    reply(500L, "第一条");
    advance(Duration.ofMinutes(3));
    reply(501L, "第二条");

    advance(Duration.ofMinutes(7));
    runReminderJob();

    assertThat(mails()).hasSize(1);
    assertThat(mails()[0].getSubject()).isEqualTo("2 条新回复：王立");
    assertThat(html(mails()[0])).contains("第一条").contains("第二条");
    assertThat(queueRows()).isZero();
  }

  /// AC：邮箱未验证时不发；封禁、注销冷静期同样不发。
  @ParameterizedTest
  @ValueSource(strings = {"UNVERIFIED", "BANNED", "PENDING_DELETION"})
  void recipient_not_eligible_is_skipped(String kind) {
    if (kind.equals("UNVERIFIED")) {
      stubMailbox(ME, MAILBOX, false, "ACTIVE");
    } else {
      stubMailbox(ME, MAILBOX, true, kind);
    }
    reply(500L, "好");

    advance(Duration.ofMinutes(10));
    runReminderJob();

    assertThat(mails()).isEmpty();
    assertThat(delivery())
        .containsEntry("status", "SKIPPED")
        .containsEntry("skip_reason", "RECIPIENT");
  }

  /// 档位为关：不发。
  @Test
  void mode_off_is_skipped() {
    setMode("REPLY", "OFF");
    reply(500L, "好");

    advance(Duration.ofMinutes(10));
    runReminderJob();

    assertThat(mails()).isEmpty();
    assertThat(delivery()).containsEntry("skip_reason", "MODE");
  }

  /// AC：邮件头带两个退订头（RFC 8058），幂等键是投递 ID；正文底部有退订确认页链接。
  @Test
  void headers_carry_one_click_unsubscribe() throws Exception {
    reply(500L, "好");
    advance(Duration.ofMinutes(10));
    runReminderJob();

    MimeMessage mail = mails()[0];
    String token =
        jdbcTemplate.queryForObject(
            "SELECT unsubscribe_token FROM preference.email_preference WHERE user_id = ?",
            String.class,
            ME);
    String deliveryId = delivery().get("delivery_id").toString();
    assertThat(mail.getHeader("List-Unsubscribe", null))
        .isEqualTo("<http://portal.test/unsubscribe/one-click?token=" + token + "&category=REPLY>");
    assertThat(mail.getHeader("List-Unsubscribe-Post", null))
        .isEqualTo("List-Unsubscribe=One-Click");
    assertThat(mail.getHeader("Resend-Idempotency-Key", null)).isEqualTo(deliveryId);
    assertThat(html(mail))
        .contains("href=\"http://portal.test/unsubscribe#token=" + token + "&amp;category=REPLY\"")
        .contains("href=\"http://portal.test/settings/notifications\"");
  }

  /// AC：identity 不可用时推迟重试，恢复后补发，只发一封。
  @Test
  void identity_down_defers_then_sends() {
    reply(500L, "好");
    advance(Duration.ofMinutes(10));
    identityDown();

    SendResult down = runReminderJob();

    assertThat(down.stoppedEarly()).isTrue();
    assertThat(mails()).isEmpty();
    assertThat(delivery()).containsEntry("status", "PENDING").containsEntry("attempts", 1);

    identity.resetAll();
    resetMailWorld();
    advance(Duration.ofMinutes(11));
    runReminderJob();
    runReminderJob();

    assertThat(mails()).hasSize(1);
    assertThat(delivery()).containsEntry("status", "SENT");
  }

  /// 调用过发信端口、临时失败：推迟后用同一个投递 ID 重发，只送达一封。
  @Test
  void transient_failure_retries_with_same_delivery_id() throws Exception {
    reply(500L, "好");
    advance(Duration.ofMinutes(10));
    flaky.failNext(1, MailDeliveryFailure.TRANSIENT);

    runReminderJob();
    Map<String, Object> deferred = delivery();
    assertThat(deferred).containsEntry("status", "PENDING").containsEntry("attempts", 1);
    assertThat(deferred.get("first_sent_at")).isNotNull();

    advance(Duration.ofMinutes(1));
    runReminderJob();

    assertThat(mails()).hasSize(1);
    String deliveryId = deferred.get("delivery_id").toString();
    assertThat(mails()[0].getHeader("Resend-Idempotency-Key", null)).isEqualTo(deliveryId);
    assertThat(mails()[0].getMessageID()).startsWith("<" + deliveryId + "@");
  }

  /// 这一封的投递记录。
  ///
  /// @return 一行
  private Map<String, Object> delivery() {
    return jdbcTemplate.queryForMap(
        "SELECT delivery_id, status, skip_reason, attempts, first_sent_at FROM mail.reminder_delivery");
  }

  /// 排队表行数。
  ///
  /// @return 行数
  private long queueRows() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM mail.reminder_queue", Long.class);
  }
}
