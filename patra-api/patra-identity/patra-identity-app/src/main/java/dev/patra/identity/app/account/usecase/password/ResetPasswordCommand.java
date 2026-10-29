package dev.patra.identity.app.account.usecase.password;

import dev.patra.tool.cqrs.Command;

/// 找回密码：用邮件里的令牌设置新密码。
///
/// @param token 令牌原文
/// @param newPassword 新密码，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`；安全通知里的设备（PAP-99）
public record ResetPasswordCommand(
    String token, String newPassword, String clientIp, String userAgent)
    implements Command<PasswordReset> {

  /// 创建命令。
  ///
  /// @param token 令牌原文
  /// @param newPassword 新密码
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 命令
  public static ResetPasswordCommand of(
      String token, String newPassword, String clientIp, String userAgent) {
    return new ResetPasswordCommand(token, newPassword, clientIp, userAgent);
  }

  /// 不输出令牌与密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ResetPasswordCommand[token=***, newPassword=***, clientIp="
        + clientIp
        + ", userAgentLength="
        + (userAgent == null ? 0 : userAgent.length())
        + "]";
  }
}
