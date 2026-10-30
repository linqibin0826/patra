package dev.patra.notification.domain.inbox.model.read;

import dev.patra.notification.domain.inbox.model.enums.NotificationType;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 信箱里的一条提醒（库里的原样，还没拼名片、对象信息与摘录）。
///
/// @param notificationId 提醒 ID
/// @param type 类型
/// @param actorId 触发人
/// @param actorAnonymous 触发人是对象的匿名所有者
/// @param subjectId 所在对象
/// @param commentId 评论，可空
/// @param plainText 评论最新版本的纯文本全文（PAP-128，含 `<@ID>`）；评论已删除或没有快照时为空
/// @param occurredAt 排序时间
/// @param readAt 已读时刻；未读为空
@Builder
public record InboxEntry(
    long notificationId,
    NotificationType type,
    long actorId,
    boolean actorAnonymous,
    long subjectId,
    Long commentId,
    String plainText,
    Instant occurredAt,
    Instant readAt) {

  /// 校验非空。
  public InboxEntry {
    Objects.requireNonNull(type, "type 不能为 null");
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
  }

  /// 是否已读。
  ///
  /// @return 已读时刻不为空
  public boolean read() {
    return readAt != null;
  }
}
