package dev.patra.interaction.domain.post.event;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;

/// 投稿经验已删除。投稿经验子域在软删、登记行置为失效的同一事务里发布；提醒子域用同步 `@EventListener` 订阅，
/// 删掉这条经验下的全部提醒（PAP-127），失败时删除一起回滚。已删除再删不发。
///
/// @param postId 帖子 ID
/// @param subjectId 这条经验自己在登记表里的对象（POST），不是经验所属期刊的对象
/// @param authorId 作者
/// @param deletedAt 删除时刻
public record PostDeletedEvent(long postId, SubjectId subjectId, long authorId, Instant deletedAt) {

  /// 创建事件。
  ///
  /// @param postId 帖子 ID
  /// @param subjectId 这条经验的登记对象
  /// @param authorId 作者
  /// @param deletedAt 删除时刻
  /// @return 事件
  public static PostDeletedEvent of(
      long postId, SubjectId subjectId, long authorId, Instant deletedAt) {
    return new PostDeletedEvent(postId, subjectId, authorId, deletedAt);
  }
}
