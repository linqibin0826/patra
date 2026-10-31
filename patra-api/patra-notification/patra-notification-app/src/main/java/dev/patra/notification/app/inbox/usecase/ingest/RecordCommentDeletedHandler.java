package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 评论已删除：先把快照标成墓碑（锁住这一行，和同一条评论的发表互斥），再删它的全部提醒（所有收件人、所有类别，
/// 包括有帮助条目）。删除先于发表到达时只留墓碑，后到的发表会被丢弃（spec 7.4）。
@Component
@RequiredArgsConstructor
public class RecordCommentDeletedHandler
    implements CommandHandler<RecordCommentDeletedCommand, IngestOutcome> {

  private final ProcessedEventPort processed;
  private final CommentContentRepository comments;
  private final NotificationRepository notifications;
  private final ApplicationEventPublisher events;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 处理。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public IngestOutcome handle(RecordCommentDeletedCommand command) {
    Instant now = clock.instant();
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              if (!processed.markProcessed(InboxConsumer.NAME, command.eventId())) {
                return IngestOutcome.DUPLICATE;
              }
              comments.markDeleted(
                  command.commentId(), command.subjectId(), command.deletedAt(), now);
              Set<Long> affected = notifications.deleteByComment(command.commentId());
              if (!affected.isEmpty()) {
                events.publishEvent(UnreadCountsChangedEvent.of(affected));
              }
              return IngestOutcome.APPLIED;
            }));
  }
}
