package dev.patra.notification.app.preference.usecase.change;

import dev.patra.notification.app.preference.usecase.query.EmailPreferenceView;
import dev.patra.tool.cqrs.Command;

/// 改当前用户某一类提醒的邮件档位（设置页一次只改一类）。两个字段都是请求原文，可空，由处理器校验。
///
/// @param category 类别：`REPLY` 或 `MENTION`
/// @param mode 档位：`OFF`、`INSTANT` 或 `DIGEST`
public record ChangeEmailModeCommand(String category, String mode)
    implements Command<EmailPreferenceView> {

  /// 构造。
  ///
  /// @param category 类别
  /// @param mode 档位
  /// @return 命令
  public static ChangeEmailModeCommand of(String category, String mode) {
    return new ChangeEmailModeCommand(category, mode);
  }
}
