package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 邮箱已注册，返回 409。并发注册撞上唯一约束时也抛它。
public final class EmailAlreadyRegisteredException extends DomainException {

  /// 创建异常。文案固定，不带邮箱。
  public EmailAlreadyRegisteredException() {
    super("该邮箱已注册", StandardErrorTrait.CONFLICT);
  }
}
