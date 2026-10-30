package dev.patra.notification.domain.inbox.model.vo;

import dev.patra.notification.domain.inbox.model.enums.NotificationType;
import java.util.Objects;

/// 一条评论要提醒的一个人。
///
/// @param userId 收件人
/// @param type 提醒类型
public record Recipient(long userId, NotificationType type) {

  /// 校验非空。
  public Recipient {
    Objects.requireNonNull(type, "type 不能为 null");
  }

  /// 构造。
  ///
  /// @param userId 收件人
  /// @param type 提醒类型
  /// @return 收件人
  public static Recipient of(long userId, NotificationType type) {
    return new Recipient(userId, type);
  }
}
