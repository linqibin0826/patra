package dev.patra.notification.domain.mail.policy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.vo.FlushPlan;
import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import dev.patra.notification.domain.mail.model.vo.ReminderBatch;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/// 离站逐条的合并窗口（spec 2.3、5.1）：一个收件人的排队行到点时，丢弃已读、已删除和出现之后上过站的；剩下的按类别看，
/// 最早一条已经等满 10 分钟的那一类，连同它还在等的全部合成一批（Jira 式窗口）；最早一条还没到点的那一类原样留着。
public final class ReminderWindowPolicy {

  /// 排队行的先后：入队时刻，同一时刻按提醒 ID。
  private static final Comparator<QueuedReminder> QUEUE_ORDER =
      Comparator.comparing(QueuedReminder::queuedAt)
          .thenComparingLong(QueuedReminder::notificationId);

  /// 只有静态方法，不实例化。
  private ReminderWindowPolicy() {}

  /// 处理一个收件人的全部排队行。
  ///
  /// @param rows 这个人的全部排队行（两类、到没到点都在内）
  /// @param unreadIds 其中仍存在且未读的提醒 ID
  /// @param lastSeen 他最后一次在站的时刻；从没出现过为 `null`
  /// @param now 此刻
  /// @return 丢弃与合并的结果
  public static FlushPlan plan(
      Collection<QueuedReminder> rows, Set<Long> unreadIds, Instant lastSeen, Instant now) {
    List<Long> dropped = new ArrayList<>();
    List<QueuedReminder> kept = new ArrayList<>();
    rows.stream()
        .sorted(QUEUE_ORDER)
        .forEach(
            row -> {
              boolean seen = lastSeen != null && !row.queuedAt().isAfter(lastSeen);
              if (!unreadIds.contains(row.notificationId()) || seen) {
                dropped.add(row.notificationId());
              } else {
                kept.add(row);
              }
            });
    List<ReminderBatch> batches = new ArrayList<>();
    for (NotificationCategory category : NotificationCategory.values()) {
      List<QueuedReminder> waiting = kept.stream().filter(r -> r.category() == category).toList();
      if (!waiting.isEmpty() && !waiting.getFirst().dueAt().isAfter(now)) {
        batches.add(
            ReminderBatch.of(
                category, waiting.stream().map(QueuedReminder::notificationId).toList()));
      }
    }
    return FlushPlan.of(dropped, batches);
  }
}
