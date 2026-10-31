package dev.patra.interaction.domain.discussion.event;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 评论已删除：变占位或整条去掉（已是占位再删不发；级联去掉的占位当初变占位时已发过）。讨论子域在写入的同一事务里
/// 发布，订阅方式同 {@link CommentPublishedEvent}；外送成集成事件 `comment.deleted`（PAP-141），通知服务据此删除这条
/// 评论的全部提醒（PAP-143）。
///
/// @param eventId 事件 ID：发布时生成，外送与补投原样带上，消费方据此去重
/// @param commentId 被删的评论
/// @param version 删除时的正文版本
/// @param subjectId 对象
/// @param authorId 作者
/// @param placeholder 留下了占位（有回复）还是整条去掉
/// @param deletedAt 删除时刻
@Builder
public record CommentDeletedEvent(
    UUID eventId,
    long commentId,
    int version,
    SubjectId subjectId,
    long authorId,
    boolean placeholder,
    Instant deletedAt) {

  /// 事件 ID 必填：不在这里兜底生成，否则补投读回登记行时会换一个 ID。外送映射要用的对象也必填。
  public CommentDeletedEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(subjectId, "subjectId");
  }
}
