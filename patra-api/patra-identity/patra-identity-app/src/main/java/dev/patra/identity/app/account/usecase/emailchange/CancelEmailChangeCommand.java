package dev.patra.identity.app.account.usecase.emailchange;

import dev.patra.tool.cqrs.Command;

/// 取消待确认的改邮箱：作废确认令牌。没有待确认也成功。
public record CancelEmailChangeCommand() implements Command<Void> {

  /// 创建命令。
  ///
  /// @return 命令
  public static CancelEmailChangeCommand of() {
    return new CancelEmailChangeCommand();
  }
}
