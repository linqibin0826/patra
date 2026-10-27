package dev.patra.interaction.domain.notification.model.vo;

import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import lombok.Builder;

/// 一条待写入的提醒。
///
/// @param recipientId 收件人
/// @param type 类型
/// @param actorId 触发人
/// @param actorAnonymous 触发人是对象的匿名所有者
/// @param subjectId 对象
/// @param commentId 评论
/// @param excerpt 摘录（已截到存储上限）
/// @param createdAt 生成时刻（事件里的时刻）
@Builder
public record NotificationDraft(
    long recipientId,
    NotificationType type,
    long actorId,
    boolean actorAnonymous,
    SubjectId subjectId,
    long commentId,
    ExcerptText excerpt,
    Instant createdAt) {}
