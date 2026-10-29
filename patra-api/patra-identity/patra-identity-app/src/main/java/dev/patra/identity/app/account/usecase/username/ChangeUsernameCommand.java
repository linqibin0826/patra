package dev.patra.identity.app.account.usecase.username;

import dev.patra.tool.cqrs.Command;

/// 当前用户改用户名。
///
/// @param username 新用户名，原始输入
public record ChangeUsernameCommand(String username) implements Command<ChangeUsernameResult> {

  /// 创建命令。
  ///
  /// @param username 新用户名
  /// @return 命令
  public static ChangeUsernameCommand of(String username) {
    return new ChangeUsernameCommand(username);
  }
}
