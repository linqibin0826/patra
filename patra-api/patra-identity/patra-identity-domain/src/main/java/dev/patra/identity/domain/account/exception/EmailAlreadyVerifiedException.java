package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 邮箱已经验证过了还要重发验证邮件，返回 409，`traits` 里带 `EMAIL_ALREADY_VERIFIED`，门户据此刷新状态。
public final class EmailAlreadyVerifiedException extends DomainException {

  /// 创建异常。
  public EmailAlreadyVerifiedException() {
    super("邮箱已经验证过了", StandardErrorTrait.CONFLICT, IdentityErrorTrait.EMAIL_ALREADY_VERIFIED);
  }
}
