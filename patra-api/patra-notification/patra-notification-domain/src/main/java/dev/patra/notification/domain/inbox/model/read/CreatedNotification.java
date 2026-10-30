package dev.patra.notification.domain.inbox.model.read;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.time.Instant;
import java.util.Objects;

/// 刚写入信箱的一条提醒（给提醒邮件排离站检查用，PAP-146）。
///
/// @param notificationId 提醒 ID
/// @param recipientId 收件人
/// @param category 类别
/// @param occurredAt 排序时间
public record CreatedNotification(
    long notificationId, long recipientId, NotificationCategory category, Instant occurredAt) {

  /// 校验非空。
  public CreatedNotification {
    Objects.requireNonNull(category, "category 不能为 null");
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
  }

  /// 构造。
  ///
  /// @param notificationId 提醒 ID
  /// @param recipientId 收件人
  /// @param category 类别
  /// @param occurredAt 排序时间
  /// @return 记录
  public static CreatedNotification of(
      long notificationId, long recipientId, NotificationCategory category, Instant occurredAt) {
    return new CreatedNotification(notificationId, recipientId, category, occurredAt);
  }
}
