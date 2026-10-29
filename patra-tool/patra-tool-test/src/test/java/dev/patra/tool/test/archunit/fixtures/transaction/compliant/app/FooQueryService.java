package dev.patra.tool.test.archunit.fixtures.transaction.compliant.app;

import org.springframework.transaction.annotation.Transactional;

/// 夹具：app 层方法级只读事务，合规。
public class FooQueryService {

  /// 查询。
  ///
  /// @return 结果
  @Transactional(readOnly = true)
  public String find() {
    return "foo";
  }
}
