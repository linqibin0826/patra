package dev.patra.notification.domain.mail.model.vo;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.util.List;
import java.util.Objects;

/// 到点后合成一封逐条邮件的一批提醒（同一个收件人、同一类）。
///
/// @param category 类别
/// @param notificationIds 提醒 ID，按入队先后
public record ReminderBatch(NotificationCategory category, List<Long> notificationIds) {

  /// 校验非空并防御性拷贝。
  public ReminderBatch {
    Objects.requireNonNull(category, "category 不能为 null");
    notificationIds = List.copyOf(notificationIds);
  }

  /// 构造。
  ///
  /// @param category 类别
  /// @param notificationIds 提醒 ID
  /// @return 一批
  public static ReminderBatch of(NotificationCategory category, List<Long> notificationIds) {
    return new ReminderBatch(category, notificationIds);
  }
}
