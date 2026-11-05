package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.domain.account.model.enums.IdentityProvider;

/// 绑定完成。
///
/// @param provider 平台
/// @param accountLabel 账号标识
public record ProviderLinked(IdentityProvider provider, String accountLabel) {

  /// 创建结果。
  ///
  /// @param provider 平台
  /// @param accountLabel 账号标识
  /// @return 结果
  public static ProviderLinked of(IdentityProvider provider, String accountLabel) {
    return new ProviderLinked(provider, accountLabel);
  }
}
