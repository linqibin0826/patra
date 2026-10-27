package dev.patra.interaction.domain.notification.model.vo;

import dev.patra.interaction.domain.notification.model.enums.NotificationType;

/// 一条评论要提醒的一个人。
///
/// @param userId 收件人
/// @param type 提醒类型
public record Recipient(long userId, NotificationType type) {

  /// 构造。
  ///
  /// @param userId 收件人
  /// @param type 提醒类型
  /// @return 收件人
  public static Recipient of(long userId, NotificationType type) {
    return new Recipient(userId, type);
  }
}
