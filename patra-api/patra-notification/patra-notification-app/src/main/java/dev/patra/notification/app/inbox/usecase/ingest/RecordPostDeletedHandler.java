package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 投稿经验已删除：记对象墓碑（之后它下面评论的事件都丢弃），删它下面的全部提醒（回复、@、有帮助）与评论快照。
///
/// 对象墓碑没有行可锁：与同一经验下评论事件的互斥，靠信箱队列单消费线程顺序处理（spec 7.2）。
@Component
@RequiredArgsConstructor
public class RecordPostDeletedHandler
    implements CommandHandler<RecordPostDeletedCommand, IngestOutcome> {

  private final ProcessedEventPort processed;
  private final SubjectTombstoneRepository tombstones;
  private final CommentContentRepository comments;
  private final NotificationRepository notifications;
  private final ApplicationEventPublisher events;
  private final TransactionOperations transactions;

  /// 处理。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public IngestOutcome handle(RecordPostDeletedCommand command) {
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              if (!processed.markProcessed(InboxConsumer.NAME, command.eventId())) {
                return IngestOutcome.DUPLICATE;
              }
              tombstones.save(command.subjectId(), command.deletedAt());
              Set<Long> affected = notifications.deleteBySubject(command.subjectId());
              comments.deleteBySubject(command.subjectId());
              if (!affected.isEmpty()) {
                events.publishEvent(UnreadCountsChangedEvent.of(affected));
              }
              return IngestOutcome.APPLIED;
            }));
  }
}
