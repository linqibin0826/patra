package dev.patra.identity.app.account.usecase.email;

import dev.patra.tool.cqrs.Command;

/// 用邮件里的令牌验证邮箱。
///
/// @param token 令牌原文，来自落地页读到的 URL 片段
public record VerifyEmailCommand(String token) implements Command<EmailVerified> {

  /// 创建命令。
  ///
  /// @param token 令牌原文
  /// @return 命令
  public static VerifyEmailCommand of(String token) {
    return new VerifyEmailCommand(token);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "VerifyEmailCommand[token=***]";
  }
}
