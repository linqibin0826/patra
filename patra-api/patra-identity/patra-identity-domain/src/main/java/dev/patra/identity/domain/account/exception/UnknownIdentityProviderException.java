package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 路径里的 `{provider}` 不是支持的平台，返回 404。
public final class UnknownIdentityProviderException extends DomainException {

  /// 创建异常。
  public UnknownIdentityProviderException() {
    super("不支持这个平台", StandardErrorTrait.NOT_FOUND);
  }
}
