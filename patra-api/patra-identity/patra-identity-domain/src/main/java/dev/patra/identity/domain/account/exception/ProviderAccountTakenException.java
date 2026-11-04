package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 这个第三方账号已绑给别的用户，返回 409。绝不把它转绑或合并到当前账号（v0.19 决策 D）。
public final class ProviderAccountTakenException extends DomainException {

  /// 创建异常。
  public ProviderAccountTakenException() {
    super(
        "这个第三方账号已绑定其他 Patra 账号",
        StandardErrorTrait.CONFLICT,
        IdentityErrorTrait.PROVIDER_ACCOUNT_TAKEN);
  }
}
