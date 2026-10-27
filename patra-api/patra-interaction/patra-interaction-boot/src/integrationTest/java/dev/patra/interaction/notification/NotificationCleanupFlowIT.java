package dev.patra.interaction.notification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.app.notification.usecase.purge.PurgeExpiredNotificationsCommand;
import dev.patra.interaction.app.subject.usecase.cleanup.CleanupDeletedUsersCommand;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/// 清理（AC 4）：保留期任务只删超过 5 个月的；注销后收件人的提醒为 0。
class NotificationCleanupFlowIT extends NotificationFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long PAPER = 100L;

  @Autowired private CommandBus commandBus;

  @BeforeEach
  void world() {
    publication(PAPER);
    users(A, B);
  }

  /// 一条在 5 个月前 1 小时，一条在 5 个月前之后 1 小时：只删前者。
  @Test
  void should_purge_only_notifications_older_than_five_months() {
    long root = comment(A, "publication", PAPER, "根", null);
    long old = comment(B, "publication", PAPER, "旧", root);
    long recent = comment(B, "publication", PAPER, "新", root);
    age(old, "now() - interval '5 months' - interval '1 hour'");
    age(recent, "now() - interval '5 months' + interval '1 hour'");

    assertThat(commandBus.handle(PurgeExpiredNotificationsCommand.of())).isEqualTo(1);

    assertThat(count("SELECT count(*) FROM notification.notification WHERE comment_id = ?", old))
        .isZero();
    assertThat(count("SELECT count(*) FROM notification.notification WHERE comment_id = ?", recent))
        .isEqualTo(1L);
  }

  /// A 注销：他作为收件人的提醒删掉；B 收到的由 A 触发的那条还在，触发人显示为已注销。
  @Test
  void should_delete_notifications_of_deleted_user() {
    long rootA = comment(A, "publication", PAPER, "A 的根", null);
    long rootB = comment(B, "publication", PAPER, "B 的根", null);
    comment(B, "publication", PAPER, "回 A", rootA);
    comment(A, "publication", PAPER, "回 B", rootB);
    IDENTITY_DATA.put(A, "DELETED", false, "PUBLIC");

    commandBus.handle(CleanupDeletedUsersCommand.of());

    assertThat(count("SELECT count(*) FROM notification.notification WHERE recipient_id = ?", A))
        .isZero();
    JsonNode item = inbox(B, "replies").get("items").get(0);
    assertThat(item.get("actorAnonymous").asBoolean()).isFalse();
    assertThat(item.get("actor").get("state").asString()).isEqualTo("DELETED");
  }

  /// 把一条评论引起的提醒的生成时刻改成给定的 SQL 表达式。
  ///
  /// @param commentId 评论
  /// @param expression SQL 时刻表达式
  private void age(long commentId, String expression) {
    jdbcTemplate.update(
        "UPDATE notification.notification SET created_at = " + expression + " WHERE comment_id = ?",
        commentId);
  }
}
