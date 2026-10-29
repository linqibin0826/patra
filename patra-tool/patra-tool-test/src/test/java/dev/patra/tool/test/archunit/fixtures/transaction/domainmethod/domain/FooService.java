package dev.patra.tool.test.archunit.fixtures.transaction.domainmethod.domain;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 夹具：domain 层方法带事务注解（哪怕是 MANDATORY），违规。
public class FooService {

  /// 执行。
  @Transactional(propagation = Propagation.MANDATORY)
  public void run() {}
}
