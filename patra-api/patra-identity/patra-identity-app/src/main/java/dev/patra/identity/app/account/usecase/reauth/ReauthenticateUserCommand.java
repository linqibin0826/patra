package dev.patra.identity.app.account.usecase.reauth;

import dev.patra.tool.cqrs.Command;

/// 再认证：当前用户重新输一次密码，通过后换新令牌并刷新会话的再认证时间。
///
/// @param password 密码，原始输入
public record ReauthenticateUserCommand(String password) implements Command<Reauthenticated> {

  /// 创建命令。
  ///
  /// @param password 密码
  /// @return 命令
  public static ReauthenticateUserCommand of(String password) {
    return new ReauthenticateUserCommand(password);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ReauthenticateUserCommand[password=***]";
  }
}
