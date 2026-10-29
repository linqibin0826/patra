package dev.patra.identity.app.session.usecase.logout;

import dev.patra.tool.cqrs.Command;

/// 登出当前用户。没有字段：当前用户从 `CurrentUserPort` 取。
public record LogoutUserCommand() implements Command<Void> {

  /// 创建命令。
  ///
  /// @return 命令
  public static LogoutUserCommand of() {
    return new LogoutUserCommand();
  }
}
