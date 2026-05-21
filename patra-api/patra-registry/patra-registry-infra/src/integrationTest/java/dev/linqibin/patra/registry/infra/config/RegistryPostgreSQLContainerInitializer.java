package dev.linqibin.patra.registry.infra.config;

import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;

/// Registry 服务专用 PostgreSQL 容器初始化器。
///
/// 继承 starter-test 的 {@link PostgreSQLContainerInitializer}，指定 registry 服务的数据库名。
///
/// ### 使用方式
///
/// ```java
/// @DataJpaTest
/// @ContextConfiguration(initializers = RegistryPostgreSQLContainerInitializer.class)
/// class SomeRepositoryIT {
///     // ...
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see PostgreSQLContainerInitializer
public class RegistryPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {

  /// 返回 registry 服务的数据库名。
  ///
  /// @return 数据库名 "patra_registry"
  @Override
  protected String getDatabaseName() {
    return "patra_registry";
  }
}
