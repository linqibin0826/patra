package dev.patra.interaction.infra.config;

import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;

/// interaction 集成测试用的 PostgreSQL 容器，库名 `patra_interaction`；infra 与 boot 的集成测试共用。
public class InteractionITPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {

  /// 返回库名。
  ///
  /// @return 库名
  @Override
  protected String getDatabaseName() {
    return "patra_interaction";
  }
}
