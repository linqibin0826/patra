package dev.patra.notification.domain.mail.model.vo;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 一条在等 10 分钟的提醒（spec 5.1、6.1）：写入信箱时入队，到点后决定发不发、和谁合成一封。
///
/// @param notificationId 信箱里的提醒
/// @param recipientId 收件人
/// @param category 类别，只有回复与 @
/// @param queuedAt 入队时刻，即提醒写入信箱的时刻；在站复查按它比
/// @param dueAt 到点时刻
@Builder
public record QueuedReminder(
    long notificationId,
    long recipientId,
    NotificationCategory category,
    Instant queuedAt,
    Instant dueAt) {

  /// 离站逐条要等多久。
  public static final Duration WAIT = Duration.ofMinutes(10);

  /// 校验非空。
  public QueuedReminder {
    Objects.requireNonNull(category, "category 不能为 null");
    Objects.requireNonNull(queuedAt, "queuedAt 不能为 null");
    Objects.requireNonNull(dueAt, "dueAt 不能为 null");
  }

  /// 一条刚写入信箱的提醒入队。
  ///
  /// @param notificationId 提醒 ID
  /// @param recipientId 收件人
  /// @param category 类别
  /// @param createdAt 写入信箱的时刻
  /// @return 排队行，`createdAt + 10 分钟` 到点
  /// @throws IllegalArgumentException 类别没有邮件（有帮助）
  public static QueuedReminder queue(
      long notificationId, long recipientId, NotificationCategory category, Instant createdAt) {
    if (!category.hasEmail()) {
      throw new IllegalArgumentException("没有邮件的类别不排队: " + category);
    }
    return new QueuedReminder(
        notificationId, recipientId, category, createdAt, createdAt.plus(WAIT));
  }
}
