package dev.patra.notification.domain.preference.model.read;

import java.util.Arrays;
import java.util.Optional;

/// 一类提醒的邮件档位。放在公开包 `model.read`：mail 发信前要读它（spec 第 9 节）。
public enum EmailMode {

  /// 关：只在站内提醒，不发邮件。
  OFF,

  /// 离站时逐条：提醒 10 分钟后仍未读、这段时间不在站上，就发一封；默认档位。
  INSTANT,

  /// 每日汇总：每天北京时间 8:00 发一封，列出过去 24 小时里还没读的。
  DIGEST;

  /// 按名字解析，只认大写的名字。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 档位；不认识时为空
  public static Optional<EmailMode> parse(String raw) {
    return Arrays.stream(values()).filter(m -> m.name().equals(raw)).findFirst();
  }
}
