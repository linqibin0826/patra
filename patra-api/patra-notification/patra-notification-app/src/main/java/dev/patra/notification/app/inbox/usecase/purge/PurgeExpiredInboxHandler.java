package dev.patra.notification.app.inbox.usecase.purge;

import dev.patra.notification.domain.inbox.policy.InboxRetention;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 保留期清理（spec 第 12 节）：截止时刻是北京时间减 5 个自然月。先删提醒，再删没有提醒引用的评论快照（含墓碑），
/// 最后删经验墓碑；三条语句各自一个短事务，量小，一条语句删完。
@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeExpiredInboxHandler
    implements CommandHandler<PurgeExpiredInboxCommand, PurgeResult> {

  private final NotificationRepository notifications;
  private final CommentContentRepository comments;
  private final SubjectTombstoneRepository tombstones;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 清理。
  ///
  /// @param command 命令
  /// @return 各删了几行
  @Override
  public PurgeResult handle(PurgeExpiredInboxCommand command) {
    Instant cutoff = InboxRetention.cutoff(clock.instant());
    int deletedNotifications =
        Objects.requireNonNull(
            transactions.execute(status -> notifications.deleteOccurredBefore(cutoff)));
    int deletedComments =
        Objects.requireNonNull(
            transactions.execute(status -> comments.purgeUnreferencedBefore(cutoff)));
    int deletedTombstones =
        Objects.requireNonNull(transactions.execute(status -> tombstones.purgeBefore(cutoff)));
    log.info(
        "信箱保留期清理完成: cutoff={}, notifications={}, comments={}, tombstones={}",
        cutoff,
        deletedNotifications,
        deletedComments,
        deletedTombstones);
    return PurgeResult.of(deletedNotifications, deletedComments, deletedTombstones);
  }
}
