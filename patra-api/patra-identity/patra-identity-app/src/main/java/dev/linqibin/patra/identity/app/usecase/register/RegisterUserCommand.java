package dev.linqibin.patra.identity.app.usecase.register;

import dev.linqibin.commons.cqrs.Command;

/// 注册前台用户。字段是用户的原始输入，校验在处理器里做。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
public record RegisterUserCommand(String email, String password)
    implements Command<RegisterUserResult> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 命令
  public static RegisterUserCommand of(String email, String password) {
    return new RegisterUserCommand(email, password);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterUserCommand[email=" + email + ", password=***]";
  }
}
