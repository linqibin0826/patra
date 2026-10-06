package dev.linqibin.patra.identity.app.usecase.authenticate;

import dev.linqibin.commons.cqrs.Command;

/// 校验前台用户的登录凭据。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
public record AuthenticateUserCommand(String email, String password)
    implements Command<AuthenticateUserResult> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 命令
  public static AuthenticateUserCommand of(String email, String password) {
    return new AuthenticateUserCommand(email, password);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AuthenticateUserCommand[email=" + email + ", password=***]";
  }
}
