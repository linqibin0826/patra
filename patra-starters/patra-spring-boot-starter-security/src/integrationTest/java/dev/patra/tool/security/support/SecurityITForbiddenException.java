package dev.patra.tool.security.support;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 集成测试专用的领域异常：模拟业务代码判定「登录了但不允许」。
public class SecurityITForbiddenException extends DomainException {

  /// 创建带 `FORBIDDEN` 特征的异常。
  public SecurityITForbiddenException() {
    super("Probe forbidden", StandardErrorTrait.FORBIDDEN);
  }
}
