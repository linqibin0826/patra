package dev.patra.interaction.app.reaction.listener;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/// 订阅评论与投稿经验的删除事件，在删除的同一事务里删掉被删目标收到的全部有帮助、扣回获赞（spec §5）。
///
/// 同步 `@EventListener`：与发布方同一线程、同一事务；抛出异常时删除一起回滚。不加 `@Transactional`，也不能用
/// `@TransactionalEventListener`（提交后才执行，有帮助会留下）。
///
/// - 先拿目标锁：与「点」在同一把锁上排队，删除提交前插进来的明细也会被这里的 `DELETE` 看到（spec §4.3）。
/// - 评论变占位与整条去掉都处理。依赖事件语义：级联去掉的占位不再发事件，它变占位时有帮助已经清掉。
/// - 经验删除只清经验自己；它下面的评论没有被删，收到的有帮助与获赞保留（2026-10-29 用户定）。
@Component
@RequiredArgsConstructor
public class HelpfulTargetDeletedListener {

  private final HelpfulRepository helpful;

  /// 评论已删除（变占位或整条去掉）。
  ///
  /// @param event 事件
  @EventListener
  public void on(CommentDeletedEvent event) {
    clear(HelpfulTarget.of(HelpfulTargetType.COMMENT, event.commentId()));
  }

  /// 投稿经验已删除。
  ///
  /// @param event 事件
  @EventListener
  public void on(PostDeletedEvent event) {
    clear(HelpfulTarget.of(HelpfulTargetType.POST, event.postId()));
  }

  /// 拿目标锁、删明细与统计行，按用户 ID 升序扣回获赞（多个用户行的固定加锁顺序）。
  ///
  /// @param target 被删的目标
  private void clear(HelpfulTarget target) {
    try {
      helpful.lockTarget(target);
      Map<Long, Integer> credited = new TreeMap<>(helpful.deleteAllOn(target));
      credited.forEach((userId, count) -> helpful.adjustReceived(userId, -count));
    } catch (RuntimeException ex) {
      throw new HelpfulWriteFailedException(ex);
    }
  }
}
