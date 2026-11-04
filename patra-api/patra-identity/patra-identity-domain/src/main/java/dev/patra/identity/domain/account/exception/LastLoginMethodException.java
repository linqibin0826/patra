package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 没有密码且只剩这一种登录方式，解绑后就进不来了，返回 409。
public final class LastLoginMethodException extends DomainException {

  /// 创建异常。
  public LastLoginMethodException() {
    super(
        "这是你唯一的登录方式，请先设置密码再解绑", StandardErrorTrait.CONFLICT, IdentityErrorTrait.LAST_LOGIN_METHOD);
  }
}
