package dev.patra.tool.test.archunit.fixtures.transaction.infrarequired.infra;

import org.springframework.transaction.annotation.Transactional;

/// 夹具：infra 层方法开事务（默认 REQUIRED），违规。
public class FooRepositoryAdapter {

  /// 保存。
  @Transactional
  public void save() {}
}
