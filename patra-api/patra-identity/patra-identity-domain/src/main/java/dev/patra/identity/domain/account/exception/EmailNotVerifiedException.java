package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 绑定第三方账号要求邮箱已验证，返回 409。
public final class EmailNotVerifiedException extends DomainException {

  /// 创建异常。
  public EmailNotVerifiedException() {
    super("请先验证邮箱，再绑定第三方账号", StandardErrorTrait.CONFLICT, IdentityErrorTrait.EMAIL_NOT_VERIFIED);
  }
}
