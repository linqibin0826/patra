package com.patra.ingest.infra.config;

import com.patra.starter.test.container.initializer.MySQLContainerInitializer;

/// Ingest 服务专用 MySQL 容器初始化器。
///
/// 继承 starter-test 的 {@link MySQLContainerInitializer}，指定 ingest 服务的数据库名。
///
/// ### 使用方式
///
/// ```java
/// @DataJpaTest
/// @ContextConfiguration(initializers = IngestMySQLContainerInitializer.class)
/// class SomeRepositoryIT {
///     // ...
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see MySQLContainerInitializer
public class IngestMySQLContainerInitializer extends MySQLContainerInitializer {

  /// 返回 ingest 服务的数据库名。
  ///
  /// @return 数据库名 "patra_ingest"
  @Override
  protected String getDatabaseName() {
    return "patra_ingest";
  }
}
