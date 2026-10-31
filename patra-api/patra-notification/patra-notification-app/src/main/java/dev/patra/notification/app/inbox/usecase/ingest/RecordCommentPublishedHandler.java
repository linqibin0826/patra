package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.notification.app.inbox.service.NotificationWriter;
import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.notification.domain.inbox.model.vo.Recipient;
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

/// 评论已发表：按收件人规则写提醒（spec 7.2、7.3）。
///
/// 一个事务里依次：去重 → 经验墓碑 → 写评论快照（锁行；是墓碑就丢弃）→ 写提醒并发事件。收件人在事务前算好（纯计算）。
@Component
@RequiredArgsConstructor
public class RecordCommentPublishedHandler
    implements CommandHandler<RecordCommentPublishedCommand, IngestOutcome> {

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
  public IngestOutcome handle(RecordCommentPublishedCommand command) {
    CommentFacts facts = command.facts();
    List<Recipient> recipients = RecipientRules.forPublished(facts);
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
                  recipients.stream().map(r -> facts.draftFor(r.userId(), r.type(), now)).toList());
              return IngestOutcome.APPLIED;
            }));
  }
}
