package dev.linqibin.patra.catalog.infra.config;

import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;

/// Catalog 服务专用 PostgreSQL 容器初始化器。
///
/// 继承 starter-test 的 {@link PostgreSQLContainerInitializer}，指定 catalog 服务的数据库名。
///
/// ### 使用方式
///
/// ```java
/// @DataJpaTest
/// @ContextConfiguration(initializers = CatalogPostgreSQLContainerInitializer.class)
/// class SomeRepositoryIT {
///     // ...
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see PostgreSQLContainerInitializer
public class CatalogPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {

  /// 返回 catalog 服务的数据库名。
  ///
  /// @return 数据库名 "patra_catalog"
  @Override
  protected String getDatabaseName() {
    return "patra_catalog";
  }
}
