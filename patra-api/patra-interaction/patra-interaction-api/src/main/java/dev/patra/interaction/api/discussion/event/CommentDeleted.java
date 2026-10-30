package dev.patra.interaction.api.discussion.event;

import dev.patra.tool.core.event.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/// 集成事件：评论已删除（变占位或整条去掉；交换机 `interaction.events`，路由键 {@value #ROUTING_KEY}，PAP-141）。
///
/// 已是占位再删不发；整条去掉时沿父链级联去掉的占位不发（它们变占位时已经发过）。删除是终态：消费方按 `commentId`
/// 记墓碑，之后到的这条评论的发表、编辑都丢弃。
///
/// @param eventId 事件 ID：补投、重投都不变，消费方据此去重
/// @param commentId 被删的评论
/// @param version 删除时的正文版本
/// @param subjectId 所在对象
/// @param authorId 作者
/// @param placeholder 留下了占位（有回复）还是整条去掉
/// @param deletedAt 删除时刻
@Builder
public record CommentDeleted(
    UUID eventId,
    long commentId,
    int version,
    long subjectId,
    long authorId,
    boolean placeholder,
    Instant deletedAt)
    implements IntegrationEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "comment.deleted";
}
