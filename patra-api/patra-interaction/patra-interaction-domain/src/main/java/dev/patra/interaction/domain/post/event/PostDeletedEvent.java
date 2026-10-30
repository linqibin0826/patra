package dev.patra.interaction.domain.post.event;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 投稿经验已删除。投稿经验子域在软删、登记行置为失效的同一事务里发布；提醒子域用同步 `@EventListener` 订阅，
/// 删掉这条经验下的全部提醒（PAP-127），失败时删除一起回滚。已删除再删不发。同时外送成集成事件 `post.deleted`
/// （PAP-141）。
///
/// @param eventId 事件 ID：发布时生成，外送与补投原样带上，消费方据此去重
/// @param postId 帖子 ID
/// @param subjectId 这条经验自己在登记表里的对象（POST），不是经验所属期刊的对象
/// @param authorId 作者
/// @param deletedAt 删除时刻
@Builder
public record PostDeletedEvent(
    UUID eventId, long postId, SubjectId subjectId, long authorId, Instant deletedAt) {

  /// 事件 ID 必填：不在这里兜底生成，否则补投读回登记行时会换一个 ID。外送映射要用的对象也必填。
  public PostDeletedEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(subjectId, "subjectId");
  }
}
