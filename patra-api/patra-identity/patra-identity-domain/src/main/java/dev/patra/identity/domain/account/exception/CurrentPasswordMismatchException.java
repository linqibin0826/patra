package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 再认证或改密码时当前密码不正确，返回 401。除了 `UNAUTHORIZED` 还带
/// {@link IdentityErrorTrait#PASSWORD_MISMATCH}：这个 401 发生在已登录的接口上，门户不能把它当会话失效。
public final class CurrentPasswordMismatchException extends DomainException {

  /// 创建异常。
  public CurrentPasswordMismatchException() {
    super("密码不正确", StandardErrorTrait.UNAUTHORIZED, IdentityErrorTrait.PASSWORD_MISMATCH);
  }
}
