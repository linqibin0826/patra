package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 邮箱或密码错误，返回 401。邮箱不存在和密码错误都抛它，从响应上区分不出来。
public final class InvalidCredentialsException extends DomainException {

  /// 创建异常。
  public InvalidCredentialsException() {
    super("邮箱或密码错误", StandardErrorTrait.UNAUTHORIZED);
  }
}
