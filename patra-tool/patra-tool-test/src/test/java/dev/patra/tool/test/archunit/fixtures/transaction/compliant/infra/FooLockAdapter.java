package dev.patra.tool.test.archunit.fixtures.transaction.compliant.infra;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 夹具：infra 层只用 `MANDATORY` 守卫，合规。
public class FooLockAdapter {

  /// 加锁。
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock() {}
}
