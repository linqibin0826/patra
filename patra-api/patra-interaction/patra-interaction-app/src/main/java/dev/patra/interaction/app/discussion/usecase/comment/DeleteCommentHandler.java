package dev.patra.interaction.app.discussion.usecase.comment;

import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 删除自己的评论（spec 5.6）：只有作者能删，不查发表资格。在楼锁里判断：有直接回复变占位（清正文与修改历史），
/// 没有就整条去掉，再沿父评论往上，去掉已经没有回复的占位；已是占位再删什么都不做。
///
/// 统计与删除同事务（PAP-132）：一条未删除的评论没了，楼内回复数（是回复时）与对象评论数各 −1；级联去掉的占位本来不
/// 计数，链一直去到根时删它的楼统计行。
///
/// 由 `CommentAggregate#delete` 判断并产生 `CommentDeletedEvent`（已是占位时为空，不发），写库方式按事件的 `placeholder`
/// 决定。发布放在事务最后：有帮助子域的同步监听器在发布时当场执行，要拿目标锁、改用户行。这里的锁顺序因此是楼锁 →
/// 评论行（含级联的父评论）→ 目标锁 → 用户行。点有帮助也拿目标锁，但它经 `DiscussionReadPort#findById` 读评论、不加
/// 行锁，两边不会互相等成死锁；那边改成锁评论行时要重新核对这里的顺序。
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
          return comment
              .delete(actor, comments.hasReplies(id), now)
              .map(
                  event -> {
                    DeleteResult result =
                        event.placeholder() ? leavePlaceholder(comment, now) : remove(comment);
                    events.publishEvent(event);
                    return result;
                  })
              .orElseGet(() -> DeleteResult.of(true, List.of()));
        });
  }

  /// 评论有直接回复：写库标成已删除的占位，并扣统计。
  ///
  /// @param comment 锁内读到的评论
  /// @param now 删除时刻
  /// @return 留下占位，没有评论被去掉
  private DeleteResult leavePlaceholder(CommentAggregate comment, Instant now) {
    comments.markDeleted(comment.getCommentId(), now);
    uncount(comment);
    return DeleteResult.of(true, List.of());
  }

  /// 整条去掉，并级联去掉已经没有回复的占位。
  ///
  /// @param comment 锁内读到的评论
  /// @return 没有留下占位，并列出去掉的评论 ID
  private DeleteResult remove(CommentAggregate comment) {
    comments.delete(comment.getCommentId());
    uncount(comment);
    List<Long> removedIds = removeOrphanedAncestors(comment);
    if (removedIds.contains(comment.getRootId())) {
      comments.dropThreadStats(comment.getRootId());
    }
    return DeleteResult.of(false, removedIds);
  }

  /// 一条未删除的评论变占位或被去掉：是回复时楼内回复数 −1，对象评论数 −1。
  ///
  /// @param comment 锁内读到的评论（删除前是正常状态）
  private void uncount(CommentAggregate comment) {
    if (!comment.isRoot()) {
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
      if (!parent.isOrphanedPlaceholder(comments.hasReplies(parentId))) {
        break;
      }
      comments.delete(parentId);
      removedIds.add(parentId);
      parentId = parent.getParentId();
    }
    return removedIds;
  }
}
