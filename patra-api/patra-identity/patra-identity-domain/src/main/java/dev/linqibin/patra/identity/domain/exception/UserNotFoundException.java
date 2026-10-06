package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 用户不存在，返回 404。只用于后台接口。
public final class UserNotFoundException extends DomainException {

  /// 创建异常。
  public UserNotFoundException() {
    super("用户不存在", StandardErrorTrait.NOT_FOUND);
  }
}
