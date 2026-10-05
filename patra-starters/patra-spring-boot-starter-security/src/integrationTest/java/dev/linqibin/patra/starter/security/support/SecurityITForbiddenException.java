package dev.linqibin.patra.starter.security.support;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 集成测试专用的领域异常：模拟业务代码判定「登录了但不允许」。
public class SecurityITForbiddenException extends DomainException {

  /// 创建带 `FORBIDDEN` 特征的异常。
  public SecurityITForbiddenException() {
    super("Probe forbidden", StandardErrorTrait.FORBIDDEN);
  }
}
