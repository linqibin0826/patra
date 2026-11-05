package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.cqrs.Command;

/// 发起绑定之前的检查：通过了才去平台。
///
/// @param provider 平台
public record PrepareProviderLinkCommand(IdentityProvider provider)
    implements Command<CurrentUser> {

  /// 创建命令。
  ///
  /// @param provider 平台
  /// @return 命令
  public static PrepareProviderLinkCommand of(IdentityProvider provider) {
    return new PrepareProviderLinkCommand(provider);
  }
}
