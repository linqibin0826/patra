package dev.linqibin.patra.ingest.integration.config;

import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;

/// Ingest 服务专用 PostgreSQL 容器初始化器。
///
/// 继承 starter-test 的 {@link PostgreSQLContainerInitializer}，指定 ingest 服务的数据库名。
///
/// ### 使用方式
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = IngestPostgreSQLContainerInitializer.class)
/// class SomeRepositoryIT {
///     // ...
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see PostgreSQLContainerInitializer
public class IngestPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {

  /// 返回 ingest 服务的数据库名。
  ///
  /// @return 数据库名 "patra_ingest"
  @Override
  protected String getDatabaseName() {
    return "patra_ingest";
  }
}
