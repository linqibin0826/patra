package dev.patra.notification.adapter.inbox.mq;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.notification.app.inbox.usecase.ingest.InboxConsumer;
import dev.patra.notification.app.inbox.usecase.ingest.IngestOutcome;
import dev.patra.notification.app.inbox.usecase.ingest.RecordCommentDeletedCommand;
import dev.patra.notification.app.inbox.usecase.ingest.RecordCommentEditedCommand;
import dev.patra.notification.app.inbox.usecase.ingest.RecordCommentPublishedCommand;
import dev.patra.notification.app.inbox.usecase.ingest.RecordHelpfulMarkedCommand;
import dev.patra.notification.app.inbox.usecase.ingest.RecordPostDeletedCommand;
import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/// 信箱队列 `notification.inbox` 的监听：interaction 的评论发表 / 编辑 / 删除、经验删除、有帮助已点。
///
/// 一个队列多种事件，按消息头 `__TypeId__` 分到各个 `@RabbitHandler`（`patra.events.consumer.trusted-packages` 里写着
/// 这些事件所在的包）。只做「集成事件 → 命令」的映射，去重与事务在处理器里。
///
/// **单消费线程（`concurrency = "1"`）是正确性前提之一**：对象墓碑没有行可锁，经验删除与它下面评论事件之间的互斥靠
/// 顺序处理（spec 7.2）；改并发之前先给对象墓碑补锁。失败按 1、2、4 秒重试三次，之后进 `notification.inbox.dlq`。
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = InboxConsumer.NAME, concurrency = "1")
public class InboxEventListener {

  private final CommandBus commandBus;

  /// 评论已发表。
  ///
  /// @param event 事件
  @RabbitHandler
  public void on(CommentPublished event) {
    log(
        "comment.published",
        event.eventId(),
        commandBus.handle(
            RecordCommentPublishedCommand.of(
                event.eventId(),
                facts(
                    event.commentId(),
                    event.version(),
                    event.subject(),
                    event.parentCommentId(),
                    event.parentAuthorId(),
                    event.authorId(),
                    event.authorAnonymous(),
                    event.mentionedUserIds(),
                    event.plainText(),
                    event.publishedAt()))));
  }

  /// 评论已编辑。
  ///
  /// @param event 事件
  @RabbitHandler
  public void on(CommentEdited event) {
    log(
        "comment.edited",
        event.eventId(),
        commandBus.handle(
            RecordCommentEditedCommand.of(
                event.eventId(),
                facts(
                    event.commentId(),
                    event.version(),
                    event.subject(),
                    event.parentCommentId(),
                    event.parentAuthorId(),
                    event.authorId(),
                    event.authorAnonymous(),
                    event.mentionedUserIds(),
                    event.plainText(),
                    event.editedAt()),
                event.addedMentionedUserIds())));
  }

  /// 评论已删除。
  ///
  /// @param event 事件
  @RabbitHandler
  public void on(CommentDeleted event) {
    log(
        "comment.deleted",
        event.eventId(),
        commandBus.handle(
            RecordCommentDeletedCommand.of(
                event.eventId(), event.commentId(), event.subjectId(), event.deletedAt())));
  }

  /// 投稿经验已删除。
  ///
  /// @param event 事件
  @RabbitHandler
  public void on(PostDeleted event) {
    log(
        "post.deleted",
        event.eventId(),
        commandBus.handle(
            RecordPostDeletedCommand.of(event.eventId(), event.subjectId(), event.deletedAt())));
  }

  /// 有帮助已点。经验结果（`experience`）不进命令：列表读时按对象现查。
  ///
  /// @param event 事件
  @RabbitHandler
  public void on(HelpfulMarked event) {
    log(
        "helpful.marked",
        event.eventId(),
        commandBus.handle(
            RecordHelpfulMarkedCommand.builder()
                .eventId(event.eventId())
                .targetType(event.targetType())
                .targetId(event.targetId())
                .targetAuthorId(event.targetAuthorId())
                .actorId(event.userId())
                .markedAt(event.markedAt())
                .subjectId(event.subject().subjectId())
                .subjectOwnerId(event.subject().ownerId())
                .subjectOwnerAnonymous(event.subject().ownerAnonymous())
                .targetPlainText(event.targetPlainText())
                .build()));
  }

  /// 评论事件里收件人规则要的事实。不认识的对象类型按 `null`（不当经验）。
  ///
  /// @param commentId 评论
  /// @param version 版本
  /// @param subject 所在对象
  /// @param parentCommentId 父评论
  /// @param parentAuthorId 父评论作者
  /// @param authorId 作者
  /// @param authorAnonymous 作者匿名
  /// @param mentionedUserIds @ 到的人
  /// @param plainText 纯文本
  /// @param occurredAt 发表或编辑的时刻
  /// @return 事实
  private static CommentFacts facts(
      long commentId,
      int version,
      SubjectContext subject,
      Long parentCommentId,
      Long parentAuthorId,
      long authorId,
      boolean authorAnonymous,
      List<Long> mentionedUserIds,
      String plainText,
      Instant occurredAt) {
    return CommentFacts.builder()
        .commentId(commentId)
        .version(version)
        .subjectId(subject.subjectId())
        .subjectType(SubjectType.parse(subject.type()).orElse(null))
        .subjectOwnerId(subject.ownerId())
        .parentCommentId(parentCommentId)
        .parentAuthorId(parentAuthorId)
        .authorId(authorId)
        .authorAnonymous(authorAnonymous)
        .mentionedUserIds(mentionedUserIds)
        .plainText(plainText)
        .occurredAt(occurredAt)
        .build();
  }

  /// 记一行处理结果。
  ///
  /// @param type 事件类型
  /// @param eventId 事件 ID
  /// @param outcome 结果
  private static void log(String type, UUID eventId, IngestOutcome outcome) {
    log.info("信箱处理事件: type={}, eventId={}, outcome={}", type, eventId, outcome);
  }
}
