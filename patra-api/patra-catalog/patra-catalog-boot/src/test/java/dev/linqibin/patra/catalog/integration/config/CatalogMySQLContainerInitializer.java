package dev.linqibin.patra.catalog.integration.config;

import dev.linqibin.starter.test.container.initializer.MySQLContainerInitializer;

/// Catalog 服务 E2E 测试专用 MySQL 容器初始化器。
///
/// 继承 starter-test 的 {@link MySQLContainerInitializer}，指定 catalog 服务的数据库名。
///
/// @author linqibin
/// @since 0.1.0
/// @see MySQLContainerInitializer
public class CatalogMySQLContainerInitializer extends MySQLContainerInitializer {

  /// 返回 catalog 服务的数据库名。
  ///
  /// @return 数据库名 "patra_catalog"
  @Override
  protected String getDatabaseName() {
    return "patra_catalog";
  }
}
