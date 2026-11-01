package dev.patra.analytics.infra.config;

import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;

/// analytics 集成测试的 PostgreSQL 容器初始化器：库名 `patra_analytics`。
public class AnalyticsITPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {

  /// 返回库名。
  ///
  /// @return 库名
  @Override
  protected String getDatabaseName() {
    return "patra_analytics";
  }
}
