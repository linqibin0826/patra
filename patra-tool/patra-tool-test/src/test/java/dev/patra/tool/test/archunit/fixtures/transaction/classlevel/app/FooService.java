package dev.patra.tool.test.archunit.fixtures.transaction.classlevel.app;

import org.springframework.transaction.annotation.Transactional;

/// 夹具：类级 `@Transactional`，违规。
@Transactional
public class FooService {

  /// 执行。
  public void run() {}
}
