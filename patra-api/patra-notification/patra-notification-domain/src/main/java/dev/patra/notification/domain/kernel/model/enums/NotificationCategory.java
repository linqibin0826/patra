package dev.patra.notification.domain.kernel.model.enums;

import java.util.Arrays;
import java.util.Optional;

/// 提醒类别，对应提醒页的三个标签。inbox 按它分标签、计未读；preference 按它存邮件档位；mail 按它查档位。
public enum NotificationCategory {

  /// 回复我的：有人回复我的评论、有人评论我的投稿经验。
  REPLY,

  /// @ 我的：有人在评论里 @ 我。
  MENTION,

  /// 有帮助：别人觉得我的评论或投稿经验有帮助。只在站内，没有邮件。
  HELPFUL;

  /// 这一类有没有提醒邮件。
  ///
  /// @return 「有帮助」为假（只在站内，release spec 2.1），其余为真
  public boolean hasEmail() {
    return this != HELPFUL;
  }

  /// 按名字解析，只认大写的名字。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 类别；不认识时为空
  public static Optional<NotificationCategory> parse(String raw) {
    return Arrays.stream(values()).filter(c -> c.name().equals(raw)).findFirst();
  }
}
