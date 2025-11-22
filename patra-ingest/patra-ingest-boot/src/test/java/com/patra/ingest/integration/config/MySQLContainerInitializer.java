package com.patra.ingest.integration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;

/// MySQL 容器初始化器。
///
/// 提供 MySQL 8.0.36 容器的单例管理和动态配置注入。
///
/// ### 功能特性
///
/// - **JVM 内单例**: 同一 JVM 进程内所有测试共享同一个 MySQL 容器实例，大幅提升测试速度
///   - **自动启动**: 在类加载时启动容器（静态块）
///   - **动态配置**: 自动注入 JDBC 连接配置到 Spring 测试上下文
///
/// ### 使用示例
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = MySQLContainerInitializer.class)
/// @Transactional
/// class TaskRunBatchRepositoryMpImplIT {
///
///     @Autowired
///     private TaskRunBatchRepositoryPort repository;
///
///     @Test
///     @DisplayName("应该保存并查询实体")
///     void shouldSaveAndFind() {
///         // 测试实现...
/// ```
///
/// ### 容器配置
///
/// - **镜像版本**: mysql:8.0.36 (与生产环境一致)
///   - **数据库名**: patra_ingest
///   - **用户名/密码**: root / 123456
///   - **容器复用**: JVM 内复用，JVM 间不复用（避免配置缓存污染）
///
/// ### 性能表现
///
/// - 首次启动: ~20-30 秒（类加载时执行一次）
///   - 后续测试: JVM 内复用容器，无需重启（性能提升 ~20-30 秒/测试类）
///
/// @author linqibin
/// @since 0.1.0
/// @see ApplicationContextInitializer
/// @see RocketMQContainerInitializer
public class MySQLContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(MySQLContainerInitializer.class);

  /// MySQL 容器单例实例。
  ///
  /// 容器复用策略:
  ///
  /// - **JVM 内复用**: 通过静态单例模式，同一 JVM 进程内所有测试共享此容器实例
  ///   - **JVM 间不复用**: `withReuse(false)` 表示不跨 JVM 进程复用容器
  ///   - **原因**: 避免测试配置缓存污染（不同测试运行可能有不同的配置需求）
  ///
  /// 容器配置:
  ///
  /// - **版本**: mysql:8.0.36 (与生产环境一致)
  ///   - **数据库名**: patra_ingest
  ///   - **用户名/密码**: root / 123456
  ///
  private static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("patra_ingest") // 使用与生产环境一致的数据库名
          .withUsername("root")
          .withPassword("123456")
          .withReuse(false); // 不跨 JVM 进程复用，避免配置缓存污染

  // 静态初始化块：在类加载时启动容器
  static {
    log.info("========================================");
    log.info("初始化 MySQL TestContainer");
    log.info("========================================");

    mysql.start();

    log.info("MySQL 容器已启动");
    log.info("  - JDBC URL: {}", mysql.getJdbcUrl());
    log.info("  - 用户名: {}", mysql.getUsername());
    log.info("  - 数据库名: {}", mysql.getDatabaseName());
    log.info("========================================");
  }

  /// 初始化 Spring 应用上下文，注入 MySQL 动态配置。
  ///
  /// 注入的配置项:
  ///
  /// - `spring.datasource.url`: JDBC URL (包含动态端口)
  ///   - `spring.datasource.username`: root
  ///   - `spring.datasource.password`: 123456
  ///   - `spring.datasource.driver-class-name`: com.mysql.cj.jdbc.Driver
  ///
  /// @param applicationContext Spring 应用上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    log.info("注入 MySQL 动态配置到 Spring 上下文");

    TestPropertyValues.of(
            "spring.datasource.url=" + mysql.getJdbcUrl(),
            "spring.datasource.username=" + mysql.getUsername(),
            "spring.datasource.password=" + mysql.getPassword(),
            "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver")
        .applyTo(applicationContext.getEnvironment());

    log.info("MySQL 动态配置注入完成");
  }

  /// 获取 MySQL 容器实例（供测试代码访问）。
  ///
  /// @return MySQL 容器实例
  public static MySQLContainer<?> getMySQLContainer() {
    return mysql;
  }
}
