package dev.patra.notification.domain.kernel.policy;

import dev.patra.notification.domain.kernel.model.read.UserCard;

/// 摘录里 @ 到的人显示的名字（PAP-128）：主页对收件人可见用用户名，否则用昵称，identity 不认识写「已注销用户」。
/// 站内列表与邮件共用。
public final class MentionNames {

  /// identity 不认识（已注销）的人的写法。
  public static final String DELETED_USER = "已注销用户";

  /// 只有静态方法，不实例化。
  private MentionNames() {}

  /// 一个人显示的名字（不含 `@`）。
  ///
  /// @param card 替收件人取的名片，可空
  /// @return 名字
  public static String of(UserCard card) {
    if (card == null) {
      return DELETED_USER;
    }
    if (card.profileVisible() && card.username() != null) {
      return card.username();
    }
    return card.displayName() != null ? card.displayName() : DELETED_USER;
  }
}
