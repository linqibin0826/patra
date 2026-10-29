package dev.patra.interaction.domain.discussion.event;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import lombok.Builder;

/// 评论已删除：变占位或整条去掉（已是占位再删不发；级联去掉的占位当初变占位时已发过）。讨论子域在写入的同一事务里
/// 发布，订阅方式同 {@link CommentPublishedEvent}。提醒子域据此删除这条评论的全部提醒。
///
/// @param commentId 被删的评论
/// @param subjectId 对象
/// @param authorId 作者
/// @param placeholder 留下了占位（有回复）还是整条去掉
/// @param deletedAt 删除时刻
@Builder
public record CommentDeletedEvent(
    long commentId, SubjectId subjectId, long authorId, boolean placeholder, Instant deletedAt) {}
