package dev.patra.notification.app.mail.usecase.flush;

import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.vo.FlushPlan;
import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import dev.patra.notification.domain.mail.policy.ReminderWindowPolicy;
import dev.patra.notification.domain.mail.port.repository.ReminderDeliveryRepository;
import dev.patra.notification.domain.mail.port.repository.ReminderQueueRepository;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 到点处理（spec 5.1、7.2）：按收件人 ID 升序分页取「至少有一行到点」的人，每人一个事务——取他的全部排队行，复查
/// 未读与在站，按合并窗口丢弃或合成投递，再删掉处理过的行。事务里只查库写库，没有远程调用。
///
/// 一个人出错只回滚这一人、记 ERROR，下一轮再试；游标照常前移，同一轮里每个人只处理一次。
@Slf4j
@Component
@RequiredArgsConstructor
public class FlushDueRemindersHandler
    implements CommandHandler<FlushDueRemindersCommand, FlushResult> {

  /// 一页取几个收件人。
  static final int PAGE = 100;

  private final ReminderQueueRepository queue;
  private final ReminderDeliveryRepository deliveries;
  private final InboxReadPort inbox;
  private final PresenceGateway presence;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 处理一轮。
  ///
  /// @param command 命令
  /// @return 计数
  @Override
  public FlushResult handle(FlushDueRemindersCommand command) {
    Instant now = clock.instant();
    int recipients = 0;
    int dropped = 0;
    int created = 0;
    long cursor = 0L;
    List<Long> page;
    while (!(page = queue.recipientsDue(now, cursor, PAGE)).isEmpty()) {
      cursor = page.getLast();
      for (long recipientId : page) {
        try {
          FlushPlan plan =
              Objects.requireNonNull(transactions.execute(status -> flush(recipientId, now)));
          recipients++;
          dropped += plan.dropped().size();
          created += plan.batches().size();
        } catch (RuntimeException ex) {
          log.error("离站邮件到点处理失败，下一轮再试: recipientId={}", recipientId, ex);
        }
      }
    }
    if (recipients > 0) {
      log.info("离站邮件到点处理: recipients={}, dropped={}, deliveries={}", recipients, dropped, created);
    }
    return FlushResult.of(recipients, dropped, created);
  }

  /// 处理一个收件人的全部排队行。
  ///
  /// @param recipientId 收件人
  /// @param now 此刻
  /// @return 丢弃与合并的结果
  private FlushPlan flush(long recipientId, Instant now) {
    List<QueuedReminder> rows = queue.findByRecipient(recipientId);
    Set<Long> unread =
        inbox
            .findUnread(recipientId, rows.stream().map(QueuedReminder::notificationId).toList())
            .stream()
            .map(InboxEntry::notificationId)
            .collect(Collectors.toSet());
    Instant lastSeen = presence.lastSeen(List.of(recipientId)).get(recipientId);
    FlushPlan plan = ReminderWindowPolicy.plan(rows, unread, lastSeen, now);
    plan.batches()
        .forEach(
            batch ->
                deliveries.insert(
                    ReminderDelivery.instant(
                        UUID.randomUUID(),
                        recipientId,
                        batch.category(),
                        batch.notificationIds(),
                        now)));
    queue.delete(plan.consumed());
    return plan;
  }
}
