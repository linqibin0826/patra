package dev.patra.notification.domain.preference.model.enums;

import java.util.Arrays;
import java.util.Optional;

/// 退订类别（spec 5.2）。逐条邮件的链接带 `REPLY` 或 `MENTION`，每日汇总邮件带 `DIGEST`；`ALL` 只在落地页上。
public enum UnsubscribeCategory {

  /// 「回复我的」邮件。
  REPLY,

  /// 「@ 我的」邮件。
  MENTION,

  /// 每日汇总：关掉此刻设为每日汇总的所有类别。
  DIGEST,

  /// 关闭全部提醒邮件。
  ALL;

  /// 按名字解析，只认大写的名字。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 类别；不认识时为空
  public static Optional<UnsubscribeCategory> parse(String raw) {
    return Arrays.stream(values()).filter(c -> c.name().equals(raw)).findFirst();
  }

  /// 会不会出现在退订链接里。
  ///
  /// @return `ALL` 为假，其余为真
  public boolean inLinks() {
    return this != ALL;
  }
}
