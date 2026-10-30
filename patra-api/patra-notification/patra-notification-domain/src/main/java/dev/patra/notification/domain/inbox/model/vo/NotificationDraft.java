package dev.patra.notification.domain.inbox.model.vo;

import dev.patra.notification.domain.inbox.model.enums.NotificationType;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 一条待写入的提醒。
///
/// @param recipientId 收件人
/// @param type 类型
/// @param actorId 触发人
/// @param actorAnonymous 触发人是对象的匿名所有者
/// @param subjectId 所在对象
/// @param commentId 评论；回复与 @ 必填
/// @param occurredAt 排序时间（事件里的发表或编辑时刻）
@Builder
public record NotificationDraft(
    long recipientId,
    NotificationType type,
    long actorId,
    boolean actorAnonymous,
    long subjectId,
    Long commentId,
    Instant occurredAt) {

  /// 校验非空。
  public NotificationDraft {
    Objects.requireNonNull(type, "type 不能为 null");
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
  }
}
