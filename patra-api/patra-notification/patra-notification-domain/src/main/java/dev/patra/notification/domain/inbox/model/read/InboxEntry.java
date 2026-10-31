package dev.patra.notification.domain.inbox.model.read;

import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 信箱里的一条提醒（库里的原样，还没拼名片、对象信息与摘录）。
///
/// @param notificationId 提醒 ID
/// @param type 类型
/// @param actorId 触发人
/// @param actorAnonymous 触发人是对象的匿名所有者
/// @param actorCount 触发人数：有帮助是点过的人数（取消不减），回复与 @ 恒为 1
/// @param subjectId 所在对象
/// @param commentId 评论，可空
/// @param plainText 正文全文（PAP-128，含 `<@ID>`）：评论取最新版本的快照，有帮助点经验时取条目上的经验正文；评论已删除、
///     没有快照或经验没有文字时为空
/// @param occurredAt 排序时间
/// @param createdAt 写入信箱的时刻（提醒邮件复查在站按它比）
/// @param readAt 已读时刻；未读为空
@Builder
public record InboxEntry(
    long notificationId,
    NotificationType type,
    long actorId,
    boolean actorAnonymous,
    int actorCount,
    long subjectId,
    Long commentId,
    String plainText,
    Instant occurredAt,
    Instant createdAt,
    Instant readAt) {

  /// 校验非空。
  public InboxEntry {
    Objects.requireNonNull(type, "type 不能为 null");
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
    Objects.requireNonNull(createdAt, "createdAt 不能为 null");
  }

  /// 是否已读。
  ///
  /// @return 已读时刻不为空
  public boolean read() {
    return readAt != null;
  }
}
