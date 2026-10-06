package dev.linqibin.patra.identity.config;

import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;

/// identity 集成测试用的 PostgreSQL 容器，库名 `patra_identity`。
public class IdentityITPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {

  /// 返回库名。
  ///
  /// @return 库名
  @Override
  protected String getDatabaseName() {
    return "patra_identity";
  }
}
