package dev.patra.interaction.domain.notification.model.read;

import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import lombok.Builder;

/// 提醒列表里的一行（库里的原样，还没拼名字与对象上下文）。
///
/// @param notificationId 提醒 ID
/// @param type 类型
/// @param actorId 触发人
/// @param actorAnonymous 触发人是对象的匿名所有者
/// @param subjectId 对象
/// @param commentId 评论，可空
/// @param excerpt 摘录
/// @param createdAt 生成时刻
/// @param readAt 已读时刻；未读为空
@Builder
public record NotificationEntry(
    long notificationId,
    NotificationType type,
    long actorId,
    boolean actorAnonymous,
    SubjectId subjectId,
    Long commentId,
    ExcerptText excerpt,
    Instant createdAt,
    Instant readAt) {

  /// 是否已读。
  ///
  /// @return 已读时刻不为空
  public boolean read() {
    return readAt != null;
  }
}
