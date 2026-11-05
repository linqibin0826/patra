package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 当前账号已绑了这一家，每家只能绑一个，返回 409。
public final class ProviderAlreadyLinkedException extends DomainException {

  /// 创建异常。
  public ProviderAlreadyLinkedException() {
    super(
        "你已绑定过这个平台的账号，请先解绑",
        StandardErrorTrait.CONFLICT,
        IdentityErrorTrait.PROVIDER_ALREADY_LINKED);
  }
}
