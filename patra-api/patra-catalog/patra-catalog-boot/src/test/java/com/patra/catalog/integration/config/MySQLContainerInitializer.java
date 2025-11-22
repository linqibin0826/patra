package com.patra.catalog.integration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;

/// MySQL 容器初始化器（Catalog 服务）。
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
/// class MeshImportE2ETest {
///
///     @Autowired
///     private MeshImportRepository meshImportPort;
///
///     @Test
///     @DisplayName("应该完成完整导入流程")
///     void shouldCompleteFullImportWorkflow() {
///         // 测试实现...
/// ```
///
/// ### 容器配置
///
/// - **镜像版本**: mysql:8.0.36 (与生产环境一致)
///   - **数据库名**: patra_catalog
///   - **用户名/密码**: root / 123456
///   - **容器复用**: JVM 内复用，JVM 间不复用（避免配置缓存污染）
///
/// @author linqibin
/// @since 0.1.0
/// @see ApplicationContextInitializer
public class MySQLContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(MySQLContainerInitializer.class);

  /// MySQL 容器单例实例。
  ///
  /// 容器复用策略:
  ///
  /// - **JVM 内复用**: 通过静态单例模式，同一 JVM 进程内所有测试共享此容器实例
  ///   - **JVM 间不复用**: `withReuse(false)` 表示不跨 JVM 进程复用容器
  ///
  private static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("patra_catalog")
          .withUsername("root")
          .withPassword("123456")
          .withReuse(false);

  // 静态初始化块：在类加载时启动容器
  static {
    log.info("========================================");
    log.info("初始化 MySQL TestContainer (Catalog)");
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
