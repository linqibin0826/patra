package dev.linqibin.starter.test.container.initializer;

import dev.linqibin.starter.test.container.ContainerRegistry;
import dev.linqibin.starter.test.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;

/// MySQL 容器初始化器（配置化版本）。
///
/// 提供 MySQL 8.0.36 容器的单例管理和动态配置注入。
/// 通过子类化支持不同服务使用不同的数据库名。
///
/// ### 核心特性
///
/// - **JVM 内单例**: 同一 JVM 进程内所有测试共享同一个 MySQL 容器实例
/// - **配置化数据库名**: 子类通过重写 `getDatabaseName()` 指定数据库名
/// - **动态配置注入**: 自动注入 JDBC 连接配置到 Spring 测试上下文
/// - **线程安全**: 使用双重检查锁模式确保并发安全
///
/// ### 使用方式
///
/// 方式一：直接使用（默认数据库名 `patra_test`）
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = MySQLContainerInitializer.class)
/// class SomeRepositoryIT {
///     // ...
/// }
/// ```
///
/// 方式二：子类化指定数据库名
///
/// ```java
/// public class CatalogMySQLInitializer extends MySQLContainerInitializer {
///     @Override
///     protected String getDatabaseName() {
///         return "patra_catalog";
///     }
/// }
/// ```
///
/// ### 容器配置
///
/// - **镜像版本**: mysql:8.0.36 (与生产环境一致)
/// - **用户名/密码**: root / 123456
/// - **容器复用策略**: JVM 内复用，JVM 间不复用
///
/// ### 性能表现
///
/// - 首次启动: ~20-30 秒（类加载时执行一次）
/// - 后续测试: JVM 内复用容器，无需重启
///
/// @author linqibin
/// @since 0.1.0
/// @see ContainerRegistry
/// @see ApplicationContextInitializer
public class MySQLContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(MySQLContainerInitializer.class);

  /// MySQL 镜像版本（与生产环境一致）。
  private static final String MYSQL_IMAGE = "mysql:8.0.36";

  /// 默认用户名。
  private static final String DEFAULT_USERNAME = "root";

  /// 默认密码。
  private static final String DEFAULT_PASSWORD = "123456";

  /// 初始化状态标志。
  private static volatile boolean initialized = false;

  /// 同步锁对象。
  private static final Object LOCK = new Object();

  /// 获取数据库名称。
  ///
  /// 子类可重写此方法以使用不同的数据库名。
  /// 默认返回 `patra_test`。
  ///
  /// @return 数据库名称
  protected String getDatabaseName() {
    return "patra_test";
  }

  /// 初始化 MySQL 容器（线程安全）。
  ///
  /// 使用双重检查锁模式确保容器只启动一次。
  private void initializeContainer() {
    if (!initialized) {
      synchronized (LOCK) {
        if (!initialized) {
          String databaseName = getDatabaseName();

          log.info("========================================");
          log.info("初始化 MySQL TestContainer (线程: {})", Thread.currentThread().getName());
          log.info("  - 数据库名: {}", databaseName);
          log.info("========================================");

          MySQLContainer<?> mysql =
              new MySQLContainer<>(MYSQL_IMAGE)
                  .withDatabaseName(databaseName)
                  .withUsername(DEFAULT_USERNAME)
                  .withPassword(DEFAULT_PASSWORD)
                  .withReuse(false); // 不跨 JVM 进程复用，避免配置缓存污染

          mysql.start();

          // 注册到全局容器注册表
          ContainerRegistry.register(ContainerType.MYSQL, mysql);

          log.info("MySQL 容器已启动");
          log.info("  - JDBC URL: {}", mysql.getJdbcUrl());
          log.info("  - 用户名: {}", mysql.getUsername());
          log.info("  - 数据库名: {}", mysql.getDatabaseName());
          log.info("========================================");

          initialized = true;
        } else {
          log.debug("MySQL 容器已由其他线程初始化，复用现有实例");
        }
      }
    }
  }

  /// 初始化 Spring 应用上下文，注入 MySQL 动态配置。
  ///
  /// 注入的配置项:
  ///
  /// - `spring.datasource.url`: JDBC URL (包含动态端口)
  /// - `spring.datasource.username`: root
  /// - `spring.datasource.password`: 123456
  /// - `spring.datasource.driver-class-name`: com.mysql.cj.jdbc.Driver
  ///
  /// @param applicationContext Spring 应用上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    // 确保容器已初始化
    initializeContainer();

    MySQLContainer<?> mysql = ContainerRegistry.get(ContainerType.MYSQL, MySQLContainer.class);

    if (mysql == null) {
      throw new IllegalStateException("MySQL 容器未正确注册");
    }

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
  /// @return MySQL 容器实例，如果未初始化则返回 null
  public static MySQLContainer<?> getMySQLContainer() {
    return ContainerRegistry.get(ContainerType.MYSQL, MySQLContainer.class);
  }
}
