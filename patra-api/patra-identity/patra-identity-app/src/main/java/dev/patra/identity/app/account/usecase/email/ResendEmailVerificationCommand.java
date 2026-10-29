package dev.patra.identity.app.account.usecase.email;

import dev.patra.tool.cqrs.Command;

/// 当前用户重发验证邮件。
///
/// @param clientIp 客户端 IP，进每 IP 的限流维度和令牌的签发记录，可以为 `null`
public record ResendEmailVerificationCommand(String clientIp)
    implements Command<EmailVerificationResent> {

  /// 创建命令。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 命令
  public static ResendEmailVerificationCommand of(String clientIp) {
    return new ResendEmailVerificationCommand(clientIp);
  }
}
