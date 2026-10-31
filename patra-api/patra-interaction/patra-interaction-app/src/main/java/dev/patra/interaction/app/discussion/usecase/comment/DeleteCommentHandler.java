package dev.patra.interaction.app.discussion.usecase.comment;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.enums.CommentRemoval;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 删除自己的评论（spec 5.6）：只有作者能删，不查发表资格。在楼锁里判断：有直接回复变占位（清正文与修改历史），
/// 没有就整条去掉，再沿父评论往上，去掉已经没有回复的占位；已是占位再删什么都不做。变占位或去掉时发布
/// {@link CommentDeletedEvent}。
///
/// 统计与删除同事务（PAP-132）：一条未删除的评论没了，发事件之前楼内回复数（是回复时）与对象评论数各 −1；
/// 级联去掉的占位本来不计数，链一直去到根时删它的楼统计行。
@Component
@RequiredArgsConstructor
public class DeleteCommentHandler implements CommandHandler<DeleteCommentCommand, DeleteResult> {

  private final CommentRepository comments;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /// 删除。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public DeleteResult handle(DeleteCommentCommand command) {
    long actor = currentUser.require().userId();
    long id = command.commentId();
    CommentAggregate preRead = comments.find(id).orElseThrow(CommentNotFoundException::new);
    preRead.requireAuthor(actor);
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    return transactions.execute(
        status -> {
          comments.lockThread(preRead.getRootId());
          CommentAggregate comment =
              comments.findForUpdate(id).orElseThrow(CommentNotFoundException::new);
          CommentRemoval removal = comment.removalFor(actor, comments.hasReplies(id));
          return switch (removal) {
            case NONE -> DeleteResult.of(true, List.of());
            case PLACEHOLDER -> {
              comments.markDeleted(id, now);
              uncount(comment);
              events.publishEvent(deleted(comment, true, now));
              yield DeleteResult.of(true, List.of());
            }
            case REMOVE -> {
              comments.delete(id);
              uncount(comment);
              events.publishEvent(deleted(comment, false, now));
              List<Long> removedIds = removeOrphanedAncestors(comment);
              if (removedIds.contains(comment.getRootId())) {
                comments.dropThreadStats(comment.getRootId());
              }
              yield DeleteResult.of(false, removedIds);
            }
          };
        });
  }

  /// 一条未删除的评论变占位或被去掉：是回复时楼内回复数 −1，对象评论数 −1。
  ///
  /// @param comment 锁内读到的评论（删除前是正常状态）
  private void uncount(CommentAggregate comment) {
    if (comment.getParentId() != null) {
      comments.removeReply(comment.getRootId());
    }
    comments.decrementCommentCount(comment.getSubjectId());
  }

  /// 沿父评论往上，去掉已经没有回复的占位，直到遇到正常评论、仍有回复的占位或到顶。
  ///
  /// @param removed 刚去掉的评论
  /// @return 去掉的评论：它在前，之后是级联去掉的
  private List<Long> removeOrphanedAncestors(CommentAggregate removed) {
    List<Long> removedIds = new ArrayList<>(List.of(removed.getCommentId()));
    Long parentId = removed.getParentId();
    while (parentId != null) {
      CommentAggregate parent =
          comments.findForUpdate(parentId).orElseThrow(() -> new IllegalStateException("父评论不存在"));
      if (parent.getState() != CommentState.DELETED || comments.hasReplies(parentId)) {
        break;
      }
      comments.delete(parentId);
      removedIds.add(parentId);
      parentId = parent.getParentId();
    }
    return removedIds;
  }

  /// 「评论已删除」事件。
  ///
  /// @param comment 被删的评论
  /// @param placeholder 是否留下占位
  /// @param now 删除时刻
  /// @return 事件
  private static CommentDeletedEvent deleted(
      CommentAggregate comment, boolean placeholder, Instant now) {
    return CommentDeletedEvent.builder()
        .eventId(UUID.randomUUID())
        .commentId(comment.getCommentId())
        .version(comment.getVersion())
        .subjectId(comment.getSubjectId())
        .authorId(comment.getAuthorId())
        .placeholder(placeholder)
        .deletedAt(now)
        .build();
  }
}
