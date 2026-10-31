package dev.patra.notification.domain.inbox.model.read;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 刚写入信箱的一条提醒（给提醒邮件排离站检查用，PAP-146）。
///
/// @param notificationId 提醒 ID
/// @param recipientId 收件人
/// @param category 类别
/// @param occurredAt 排序时间（评论发表或编辑的时刻）
/// @param createdAt 写入信箱的时刻：提醒邮件的时间窗口从它算
@Builder
public record CreatedNotification(
    long notificationId,
    long recipientId,
    NotificationCategory category,
    Instant occurredAt,
    Instant createdAt) {

  /// 校验非空。
  public CreatedNotification {
    Objects.requireNonNull(category, "category 不能为 null");
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
    Objects.requireNonNull(createdAt, "createdAt 不能为 null");
  }
}
