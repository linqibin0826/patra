package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 第三方授权没通过协议校验（`state`、`id_token`、授权码、主体形状），返回 422。原因不进响应。
public final class ProviderAuthorizationRejectedException extends DomainException {

  /// 创建异常。
  public ProviderAuthorizationRejectedException() {
    super("第三方登录失败，请重试", StandardErrorTrait.RULE_VIOLATION, IdentityErrorTrait.OAUTH_REJECTED);
  }
}
