package com.patra.catalog.integration.config;

import com.patra.starter.test.container.initializer.MySQLContainerInitializer;

/// Catalog 服务专用 MySQL 容器初始化器。
///
/// 继承 starter-test 的 {@link MySQLContainerInitializer}，指定 catalog 服务的数据库名。
///
/// ### 使用方式
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
/// class SomeRepositoryIT {
///     // ...
/// }
/// ```
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
