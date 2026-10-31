package dev.patra.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.app.mail.usecase.digest.ScheduleDailyDigestCommand;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/// 每日汇总端到端（PAP-146 AC，spec 5.2）：8:00 建名单，下一步发出；只列窗口里仍未读的；没有就不发；一窗口一封。
@DisplayName("每日汇总端到端")
class DailyDigestFlowIT extends ReminderMailITSupport {

  /// 北京时间 10 月 31 日 08:00：窗口是 10 月 30 日 08:00 到 10 月 31 日 08:00。
  private static final Instant EIGHT = Instant.parse("2026-10-31T00:00:00Z");

  /// AC：只列窗口里未读的 @；窗口外、已读的不列；@ 设为汇总后不再逐条发。
  @Test
  void lists_only_unread_in_window() throws Exception {
    setMode("MENTION", "DIGEST");
    clock.set(EIGHT.minus(Duration.ofHours(25)));
    mention(499L, "<@2> 窗口外的");
    clock.set(T0);
    mention(500L, "<@2> 第一条");
    mention(501L, "<@2> 第二条");
    readOne(500L);

    clock.set(EIGHT);
    commandBus.handle(ScheduleDailyDigestCommand.scheduled());
    runReminderJob();

    assertThat(mails()).hasSize(1);
    MimeMessage mail = mails()[0];
    assertThat(mail.getSubject()).isEqualTo("每日汇总：1 条未读的 @（10 月 31 日）");
    assertThat(html(mail))
        .contains("第二条")
        .doesNotContain("第一条")
        .doesNotContain("窗口外的")
        .contains("@ 我的 · 1 条")
        .contains("10 月 30 日 08:00 到 10 月 31 日 08:00（北京时间）");
  }

  /// AC：没有未读时不发。
  @Test
  void nothing_unread_sends_nothing() {
    setMode("REPLY", "DIGEST");
    reply(500L, "好");
    readAll();

    clock.set(EIGHT);
    commandBus.handle(ScheduleDailyDigestCommand.scheduled());
    runReminderJob();

    assertThat(mails()).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT skip_reason FROM mail.reminder_delivery WHERE kind = 'DIGEST'",
                String.class))
        .isEqualTo("NOTHING_UNREAD");
  }

  /// 8:00 跑过、8:10 补跑：只有一封。
  @Test
  void rerun_in_eight_oclock_hour_does_not_send_twice() {
    setMode("REPLY", "DIGEST");
    reply(500L, "好");

    clock.set(EIGHT);
    commandBus.handle(ScheduleDailyDigestCommand.scheduled());
    runReminderJob();
    advance(Duration.ofMinutes(10));
    commandBus.handle(ScheduleDailyDigestCommand.scheduled());
    runReminderJob();

    assertThat(mails()).hasSize(1);
  }

  /// 手动验收只给列出的人建。
  @Test
  void manual_run_only_targets_listed_users() {
    setMode("REPLY", "DIGEST");
    call(HttpMethod.PUT, 5L, "/me/notification-preferences/REPLY", "{\"mode\":\"DIGEST\"}");
    reply(500L, "好");

    clock.set(T0.plus(Duration.ofHours(1)));
    commandBus.handle(ScheduleDailyDigestCommand.manual(Set.of(ME)));

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT recipient_id FROM mail.reminder_delivery WHERE kind = 'DIGEST'",
                Long.class))
        .containsExactly(ME);
    runReminderJob();
    assertThat(mails()).hasSize(1);
  }

  /// 汇总邮件的退订指向「每日汇总」。
  @Test
  void digest_unsubscribe_link_uses_digest_category() throws Exception {
    setMode("REPLY", "DIGEST");
    reply(500L, "好");

    clock.set(EIGHT);
    commandBus.handle(ScheduleDailyDigestCommand.scheduled());
    runReminderJob();

    assertThat(mails()[0].getHeader("List-Unsubscribe", null)).endsWith("&category=DIGEST>");
    assertThat(html(mails()[0])).contains("退订每日汇总");
  }

  /// 把某条评论的提醒标为已读。
  ///
  /// @param commentId 评论
  private void readOne(long commentId) {
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT notification_id FROM inbox.notification WHERE comment_id = ?",
            Long.class,
            commentId);
    call(HttpMethod.PUT, ME, "/me/notifications/" + id + "/read", null);
  }
}
