package dev.patra.interaction.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/// 评论与提醒同一事务（AC 2、决策 A）：一边失败，两边都不落库。
///
/// 失败用测试里临时建的触发器制造，与监听方法的执行顺序无关：评论这边用延迟到提交时触发的约束触发器（提醒已经写了，
/// 提交才失败），提醒这边用 `BEFORE INSERT` 触发器。触发器每例结束后删掉。
class NotificationRollbackFlowIT extends NotificationFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long PAPER = 100L;

  @BeforeEach
  void world() {
    publication(PAPER);
    users(A, B);
    jdbcTemplate.execute(
        "CREATE OR REPLACE FUNCTION public.test_fail() RETURNS trigger LANGUAGE plpgsql AS"
            + " $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
  }

  @AfterEach
  void dropTriggers() {
    jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_fail_comment ON discussion.comment");
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS test_fail_notification ON notification.notification");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS public.test_fail()");
  }

  /// 评论事务在提交时失败：提醒已经写进同一事务，一起回滚，不留提醒。
  @Test
  void should_leave_no_notification_when_comment_rolls_back() {
    long root = comment(A, "publication", PAPER, "根", null);
    jdbcTemplate.execute(
        "CREATE CONSTRAINT TRIGGER test_fail_comment AFTER INSERT ON discussion.comment"
            + " DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.test_fail()");

    EntityExchangeResult<String> result = post(B, "publication", PAPER, "回复", root);

    assertThat(status(result)).isGreaterThanOrEqualTo(400);
    assertThat(count("SELECT count(*) FROM discussion.comment")).isEqualTo(1L);
    assertThat(count("SELECT count(*) FROM notification.notification")).isZero();
  }

  /// 写提醒失败：500（不是 409 / 422），评论与发表计数一起回滚。
  @Test
  void should_roll_back_comment_when_notification_write_fails() {
    long root = comment(A, "publication", PAPER, "根", null);
    jdbcTemplate.execute(
        "CREATE TRIGGER test_fail_notification BEFORE INSERT ON notification.notification"
            + " FOR EACH ROW EXECUTE FUNCTION public.test_fail()");

    EntityExchangeResult<String> result = post(B, "publication", PAPER, "回复", root);

    assertThat(status(result)).isEqualTo(500);
    assertThat(count("SELECT count(*) FROM discussion.comment")).isEqualTo(1L);
    assertThat(count("SELECT count(*) FROM subject.publish_quota WHERE user_id = ?", B)).isZero();
    assertThat(count("SELECT count(*) FROM notification.notification")).isZero();
  }

  /// 对照：没人要提醒时不写提醒表，提醒表坏了也照常发表。
  @Test
  void should_still_publish_when_nobody_to_notify() {
    long root = comment(A, "publication", PAPER, "根", null);
    jdbcTemplate.execute(
        "CREATE TRIGGER test_fail_notification BEFORE INSERT ON notification.notification"
            + " FOR EACH ROW EXECUTE FUNCTION public.test_fail()");

    assertThat(status(post(A, "publication", PAPER, "补充", root))).isEqualTo(201);
  }
}
