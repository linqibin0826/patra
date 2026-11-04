package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.tool.cqrs.Command;

/// 解绑一家第三方账号。
///
/// @param provider 平台
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`
public record UnlinkProviderAccountCommand(
    IdentityProvider provider, String clientIp, String userAgent) implements Command<Void> {

  /// 创建命令。
  ///
  /// @param provider 平台
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 命令
  public static UnlinkProviderAccountCommand of(
      IdentityProvider provider, String clientIp, String userAgent) {
    return new UnlinkProviderAccountCommand(provider, clientIp, userAgent);
  }

  /// 不输出 UA 原文。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "UnlinkProviderAccountCommand[provider=" + provider + ", clientIp=" + clientIp + "]";
  }
}
