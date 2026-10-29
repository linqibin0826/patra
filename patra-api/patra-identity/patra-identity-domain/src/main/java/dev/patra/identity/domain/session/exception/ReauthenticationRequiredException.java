package dev.patra.identity.domain.session.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 最近一次输密码已超过再认证窗口，返回 403。除了 `FORBIDDEN` 还带
/// {@link IdentityErrorTrait#REAUTH_REQUIRED}，门户据此弹密码框、调 `POST /auth/reauth` 后重试。
public final class ReauthenticationRequiredException extends DomainException {

  /// 创建异常。
  public ReauthenticationRequiredException() {
    super("请重新输入密码以继续", StandardErrorTrait.FORBIDDEN, IdentityErrorTrait.REAUTH_REQUIRED);
  }
}
