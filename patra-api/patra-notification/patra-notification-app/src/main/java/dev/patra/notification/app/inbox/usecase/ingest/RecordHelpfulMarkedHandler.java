package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.model.aggregate.HelpfulNotification;
import dev.patra.notification.domain.inbox.model.enums.HelpfulChange;
import dev.patra.notification.domain.inbox.model.enums.HelpfulTargetType;
import dev.patra.notification.domain.inbox.model.vo.HelpfulMark;
import dev.patra.notification.domain.inbox.model.vo.HelpfulTarget;
import dev.patra.notification.domain.inbox.model.vo.SubjectOwner;
import dev.patra.notification.domain.inbox.policy.HelpfulDay;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.HelpfulNotificationRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 有帮助已点：并进「被点的内容 × 北京时间一天」的合并条目（PAP-144 spec 8.2）。
///
/// 一个事务里依次：去重 → 认目标种类（不认识的确认后跳过）→ 经验墓碑 → 点评论时写评论快照（版本 0，锁行；是墓碑就丢弃）
/// → 按合并键锁行读条目 → 新建或并入 → 变了才写，作者看得见的变化才发「未读数变了」。不发「提醒生成」：有帮助不发邮件。
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordHelpfulMarkedHandler
    implements CommandHandler<RecordHelpfulMarkedCommand, IngestOutcome> {

  private final ProcessedEventPort processed;
  private final SubjectTombstoneRepository tombstones;
  private final CommentContentRepository comments;
  private final HelpfulNotificationRepository helpful;
  private final ApplicationEventPublisher events;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 处理。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public IngestOutcome handle(RecordHelpfulMarkedCommand command) {
    Optional<HelpfulTargetType> type = HelpfulTargetType.parse(command.targetType());
    HelpfulMark mark = HelpfulMark.of(command.actorId(), command.markedAt());
    SubjectOwner owner = SubjectOwner.of(command.subjectOwnerId(), command.subjectOwnerAnonymous());
    Instant now = clock.instant();
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              if (!processed.markProcessed(InboxConsumer.NAME, command.eventId())) {
                return IngestOutcome.DUPLICATE;
              }
              if (type.isEmpty()) {
                log.warn(
                    "有帮助事件的目标种类不认识，跳过: eventId={}, targetType={}",
                    command.eventId(),
                    command.targetType());
                return IngestOutcome.DISCARDED;
              }
              HelpfulTarget target =
                  HelpfulTarget.of(type.get(), command.targetId(), command.subjectId());
              if (tombstones.exists(target.subjectId())
                  || commentDeleted(target, command.targetPlainText(), now)) {
                return IngestOutcome.DISCARDED;
              }
              merge(command, target, owner, mark, now);
              return IngestOutcome.APPLIED;
            }));
  }

  /// 点评论时评论是否已删除。有正文就用它以版本 0 补快照（已有任何版本都不动），顺带拿到快照行锁；没有正文时只查墓碑
  /// （快照表要求未删除的快照有正文）。点经验时恒为假。
  ///
  /// @param target 被点的内容
  /// @param plainText 事件带来的正文，可空
  /// @param now 处理时刻
  /// @return 评论已删除为 `true`
  private boolean commentDeleted(HelpfulTarget target, String plainText, Instant now) {
    if (target.type() != HelpfulTargetType.COMMENT) {
      return false;
    }
    if (plainText == null) {
      return comments.isDeleted(target.targetId());
    }
    return !comments.saveIfNewer(target.targetId(), target.subjectId(), 0, plainText, now);
  }

  /// 新建或并入合并条目，按变化写库、发事件。
  ///
  /// @param command 命令
  /// @param target 被点的内容
  /// @param owner 所在对象的所有者
  /// @param mark 这次点击
  /// @param now 处理时刻
  private void merge(
      RecordHelpfulMarkedCommand command,
      HelpfulTarget target,
      SubjectOwner owner,
      HelpfulMark mark,
      Instant now) {
    long recipientId = command.targetAuthorId();
    Optional<HelpfulNotification> existing =
        helpful.findForUpdate(recipientId, target, HelpfulDay.of(mark.markedAt()), owner);
    if (existing.isEmpty()) {
      helpful.insert(
          HelpfulNotification.open(recipientId, target, owner, mark, command.targetPlainText()),
          now);
      events.publishEvent(UnreadCountsChangedEvent.of(List.of(recipientId)));
      return;
    }
    HelpfulNotification entry = existing.get();
    HelpfulChange change = entry.absorb(mark, command.targetPlainText());
    if (change != HelpfulChange.NONE) {
      helpful.update(entry, mark);
    }
    if (change == HelpfulChange.VISIBLE) {
      events.publishEvent(UnreadCountsChangedEvent.of(List.of(recipientId)));
    }
  }
}
