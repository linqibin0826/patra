package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.notification.app.inbox.service.NotificationWriter;
import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.notification.domain.inbox.policy.RecipientRules;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 评论已编辑：快照按版本只保留最新（晚到的旧编辑不覆盖），再给这条评论第一次 @ 到的人写 @ 提醒（spec 7.2、7.3）。
/// 顺序与发表相同；编辑先于发表到达时照常处理，发表到时正文不动、照常补回复与 @ 提醒。
@Component
@RequiredArgsConstructor
public class RecordCommentEditedHandler
    implements CommandHandler<RecordCommentEditedCommand, IngestOutcome> {

  private final ProcessedEventPort processed;
  private final SubjectTombstoneRepository tombstones;
  private final CommentContentRepository comments;
  private final NotificationWriter writer;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 处理。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public IngestOutcome handle(RecordCommentEditedCommand command) {
    CommentFacts facts = command.facts();
    List<Long> added = RecipientRules.forEdited(facts, command.addedMentionedUserIds());
    Instant now = clock.instant();
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              if (!processed.markProcessed(InboxConsumer.NAME, command.eventId())) {
                return IngestOutcome.DUPLICATE;
              }
              if (tombstones.exists(facts.subjectId())
                  || !comments.saveIfNewer(
                      facts.commentId(),
                      facts.subjectId(),
                      facts.version(),
                      facts.plainText(),
                      now)) {
                return IngestOutcome.DISCARDED;
              }
              writer.write(
                  added.stream()
                      .map(userId -> facts.draftFor(userId, NotificationType.MENTION, now))
                      .toList());
              return IngestOutcome.APPLIED;
            }));
  }
}
