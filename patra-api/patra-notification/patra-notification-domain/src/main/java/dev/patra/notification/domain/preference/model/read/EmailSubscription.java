package dev.patra.notification.domain.preference.model.read;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.util.Objects;

/// 一个人的提醒邮件订阅：两类的档位与退订令牌。给 mail 发信前读（spec 第 9 节）。
///
/// @param userId 用户 ID
/// @param reply 「回复我的」档位
/// @param mention 「@ 我的」档位
/// @param unsubscribeToken 退订令牌原文，按 spec 第 7 节拼进退订链接；`toString()` 不输出它
public record EmailSubscription(
    long userId, EmailMode reply, EmailMode mention, String unsubscribeToken) {

  /// 校验非空。
  public EmailSubscription {
    Objects.requireNonNull(reply, "reply 不能为 null");
    Objects.requireNonNull(mention, "mention 不能为 null");
    Objects.requireNonNull(unsubscribeToken, "unsubscribeToken 不能为 null");
  }

  /// 创建。
  ///
  /// @param userId 用户 ID
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  /// @param unsubscribeToken 退订令牌原文
  /// @return 订阅
  public static EmailSubscription of(
      long userId, EmailMode reply, EmailMode mention, String unsubscribeToken) {
    return new EmailSubscription(userId, reply, mention, unsubscribeToken);
  }

  /// 某一类的档位。
  ///
  /// @param category 类别
  /// @return 档位；「有帮助」永远是关
  public EmailMode modeOf(NotificationCategory category) {
    return switch (category) {
      case REPLY -> reply;
      case MENTION -> mention;
      case HELPFUL -> EmailMode.OFF;
    };
  }

  /// 不输出令牌原文。
  ///
  /// @return 形如 `EmailSubscription[userId=7, reply=INSTANT, mention=OFF, unsubscribeToken=***]`
  @Override
  public String toString() {
    return "EmailSubscription[userId="
        + userId
        + ", reply="
        + reply
        + ", mention="
        + mention
        + ", unsubscribeToken=***]";
  }
}
