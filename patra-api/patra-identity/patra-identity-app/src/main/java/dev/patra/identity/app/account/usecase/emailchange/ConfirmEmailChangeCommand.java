package dev.patra.identity.app.account.usecase.emailchange;

import dev.patra.tool.cqrs.Command;

/// 改邮箱：用新邮箱收到的令牌确认，确认后立即生效。
///
/// @param token 令牌原文
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`；安全通知里的设备（PAP-99）
public record ConfirmEmailChangeCommand(String token, String clientIp, String userAgent)
    implements Command<EmailChanged> {

  /// 创建命令。
  ///
  /// @param token 令牌原文
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 命令
  public static ConfirmEmailChangeCommand of(String token, String clientIp, String userAgent) {
    return new ConfirmEmailChangeCommand(token, clientIp, userAgent);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ConfirmEmailChangeCommand[token=***, clientIp="
        + clientIp
        + ", userAgentLength="
        + (userAgent == null ? 0 : userAgent.length())
        + "]";
  }
}
