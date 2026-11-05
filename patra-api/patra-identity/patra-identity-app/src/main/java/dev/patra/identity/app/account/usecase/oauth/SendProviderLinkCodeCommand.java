package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.tool.cqrs.Command;

/// 邮箱撞车时就地绑定：提交邮箱，发验证码。重发也是它。
///
/// @param ticket 待补填凭证原文，可以为 `null`
/// @param email 邮箱，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
public record SendProviderLinkCodeCommand(String ticket, String email, String clientIp)
    implements Command<RegistrationCodeSent> {

  /// 创建命令。
  ///
  /// @param ticket 待补填凭证原文，可以为 `null`
  /// @param email 邮箱，原始输入
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 命令
  public static SendProviderLinkCodeCommand of(String ticket, String email, String clientIp) {
    return new SendProviderLinkCodeCommand(ticket, email, clientIp);
  }

  /// 不输出凭证。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "SendProviderLinkCodeCommand[ticket=***, email="
        + email
        + ", clientIp="
        + clientIp
        + "]";
  }
}
