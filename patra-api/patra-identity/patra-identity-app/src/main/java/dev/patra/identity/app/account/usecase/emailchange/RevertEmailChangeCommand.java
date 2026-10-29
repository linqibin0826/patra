package dev.patra.identity.app.account.usecase.emailchange;

import dev.patra.tool.cqrs.Command;

/// 改邮箱：用旧邮箱收到的「不是我」令牌撤销，邮箱改回、全部会话下线、密码作废并发重设链接。
///
/// @param token 令牌原文
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`；安全通知里的设备（PAP-99）
public record RevertEmailChangeCommand(String token, String clientIp, String userAgent)
    implements Command<EmailChangeReverted> {

  /// 创建命令。
  ///
  /// @param token 令牌原文
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 命令
  public static RevertEmailChangeCommand of(String token, String clientIp, String userAgent) {
    return new RevertEmailChangeCommand(token, clientIp, userAgent);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RevertEmailChangeCommand[token=***, clientIp="
        + clientIp
        + ", userAgentLength="
        + (userAgent == null ? 0 : userAgent.length())
        + "]";
  }
}
