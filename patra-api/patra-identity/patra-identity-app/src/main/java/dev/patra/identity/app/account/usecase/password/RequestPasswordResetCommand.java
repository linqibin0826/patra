package dev.patra.identity.app.account.usecase.password;

import dev.patra.tool.cqrs.Command;

/// 找回密码：申请一封重设邮件。
///
/// @param email 邮箱，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
public record RequestPasswordResetCommand(String email, String clientIp)
    implements Command<PasswordResetRequested> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 命令
  public static RequestPasswordResetCommand of(String email, String clientIp) {
    return new RequestPasswordResetCommand(email, clientIp);
  }
}
