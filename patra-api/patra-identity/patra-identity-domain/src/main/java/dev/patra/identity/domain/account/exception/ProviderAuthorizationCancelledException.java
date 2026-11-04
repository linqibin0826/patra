package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 用户在第三方平台上点了拒绝，返回 422。门户回到发起页，不当成错误提示。
public final class ProviderAuthorizationCancelledException extends DomainException {

  /// 创建异常。
  public ProviderAuthorizationCancelledException() {
    super("你取消了授权", StandardErrorTrait.RULE_VIOLATION, IdentityErrorTrait.OAUTH_CANCELLED);
  }
}
