package dev.patra.notification.domain.mail.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.vo.FlushPlan;
import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import dev.patra.notification.domain.mail.model.vo.ReminderBatch;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/// 到点时丢弃已读与看过的、把到点那一类合成一批（spec 5.1）。
class ReminderWindowPolicyTest {

  private static final Instant T0 = Instant.parse("2026-10-30T07:02:00Z");
  private static final Instant NOW = T0.plus(Duration.ofMinutes(10));

  /// 已读或已删除的（不在未读集合里）丢弃。
  @Test
  void read_or_deleted_rows_are_dropped() {
    List<QueuedReminder> rows = List.of(reply(1L, T0), reply(2L, T0));

    FlushPlan plan = ReminderWindowPolicy.plan(rows, Set.of(2L), null, NOW);

    assertThat(plan.dropped()).containsExactly(1L);
    assertThat(plan.batches())
        .containsExactly(ReminderBatch.of(NotificationCategory.REPLY, List.of(2L)));
  }

  /// 出现之后上过站的丢弃；之后才出现的留着，等它自己到点。
  @Test
  void rows_seen_after_queueing_are_dropped() {
    List<QueuedReminder> rows =
        List.of(reply(1L, T0), reply(2L, T0.plus(Duration.ofMinutes(3))), reply(3L, minutes(7)));

    FlushPlan plan =
        ReminderWindowPolicy.plan(rows, Set.of(1L, 2L, 3L), T0.plus(Duration.ofMinutes(5)), NOW);

    assertThat(plan.dropped()).containsExactly(1L, 2L);
    assertThat(plan.batches()).isEmpty();
    assertThat(plan.consumed()).containsExactly(1L, 2L);
  }

  /// 最早一条到点时，这一类还在等的全部带上（含没到点的）。
  @Test
  void due_category_takes_all_its_waiting_rows() {
    List<QueuedReminder> rows =
        List.of(reply(1L, T0), reply(2L, minutes(3)), reply(3L, minutes(7)));

    FlushPlan plan = ReminderWindowPolicy.plan(rows, Set.of(1L, 2L, 3L), null, NOW);

    assertThat(plan.dropped()).isEmpty();
    assertThat(plan.batches())
        .containsExactly(ReminderBatch.of(NotificationCategory.REPLY, List.of(1L, 2L, 3L)));
    assertThat(plan.consumed()).containsExactly(1L, 2L, 3L);
  }

  /// 回复与 @ 同时到点，各成一批，回复在前。
  @Test
  void reply_and_mention_due_together_are_two_batches() {
    List<QueuedReminder> rows = List.of(mention(2L, T0), reply(1L, T0));

    FlushPlan plan = ReminderWindowPolicy.plan(rows, Set.of(1L, 2L), null, NOW);

    assertThat(plan.batches())
        .containsExactly(
            ReminderBatch.of(NotificationCategory.REPLY, List.of(1L)),
            ReminderBatch.of(NotificationCategory.MENTION, List.of(2L)));
  }

  /// 一类到点、另一类没到点：没到点的不动。
  @Test
  void category_not_yet_due_stays_queued() {
    List<QueuedReminder> rows = List.of(reply(1L, T0), mention(2L, minutes(5)));

    FlushPlan plan = ReminderWindowPolicy.plan(rows, Set.of(1L, 2L), null, NOW);

    assertThat(plan.batches())
        .containsExactly(ReminderBatch.of(NotificationCategory.REPLY, List.of(1L)));
    assertThat(plan.consumed()).containsExactly(1L);
  }

  /// 上过站的时刻早于所有提醒：都算没看过。
  @Test
  void seen_before_queueing_does_not_drop() {
    FlushPlan plan =
        ReminderWindowPolicy.plan(List.of(reply(1L, T0)), Set.of(1L), T0.minusSeconds(1), NOW);

    assertThat(plan.dropped()).isEmpty();
    assertThat(plan.batches()).hasSize(1);
  }

  /// T0 之后几分钟。
  ///
  /// @param minutes minutes
  /// @return 结果
  private static Instant minutes(int minutes) {
    return T0.plus(Duration.ofMinutes(minutes));
  }

  /// 一条回复提醒的排队行。
  ///
  /// @param id id
  /// @param queuedAt queuedAt
  /// @return 结果
  private static QueuedReminder reply(long id, Instant queuedAt) {
    return QueuedReminder.queue(id, 7L, NotificationCategory.REPLY, queuedAt);
  }

  /// 一条 @ 提醒的排队行。
  ///
  /// @param id id
  /// @param queuedAt queuedAt
  /// @return 结果
  private static QueuedReminder mention(long id, Instant queuedAt) {
    return QueuedReminder.queue(id, 7L, NotificationCategory.MENTION, queuedAt);
  }
}
