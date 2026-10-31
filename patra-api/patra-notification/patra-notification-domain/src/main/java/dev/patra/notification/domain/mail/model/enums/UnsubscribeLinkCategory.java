package dev.patra.notification.domain.mail.model.enums;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;

/// 提醒邮件里退订链接带的类别（PAP-142 第 7 节）：逐条邮件跟着提醒类别，每日汇总邮件是 `DIGEST`。
public enum UnsubscribeLinkCategory {

  /// 「回复我的」逐条邮件。
  REPLY,

  /// 「@ 我的」逐条邮件。
  MENTION,

  /// 每日汇总邮件。
  DIGEST;

  /// 逐条邮件的退订类别。
  ///
  /// @param category 提醒类别
  /// @return 同名的退订类别
  /// @throws IllegalArgumentException 有帮助：它没有邮件
  public static UnsubscribeLinkCategory of(NotificationCategory category) {
    return switch (category) {
      case REPLY -> REPLY;
      case MENTION -> MENTION;
      case HELPFUL -> throw new IllegalArgumentException("有帮助没有提醒邮件");
    };
  }
}
