package com.patra.ingest.integration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL 容器初始化器。
 *
 * <p>提供 MySQL 8.0.36 容器的单例管理和动态配置注入。
 *
 * <h3>功能特性</h3>
 *
 * <ul>
 *   <li><strong>JVM 内单例</strong>: 同一 JVM 进程内所有测试共享同一个 MySQL 容器实例，大幅提升测试速度
 *   <li><strong>自动启动</strong>: 在类加载时启动容器（静态块）
 *   <li><strong>动态配置</strong>: 自动注入 JDBC 连接配置到 Spring 测试上下文
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @SpringBootTest
 * @ContextConfiguration(initializers = MySQLContainerInitializer.class)
 * @Transactional
 * class TaskRunBatchRepositoryMpImplIT {
 *
 *     @Autowired
 *     private TaskRunBatchRepositoryPort repository;
 *
 *     @Test
 *     @DisplayName("应该保存并查询实体")
 *     void shouldSaveAndFind() {
 *         // 测试实现...
 *     }
 * }
 * }</pre>
 *
 * <h3>容器配置</h3>
 *
 * <ul>
 *   <li><strong>镜像版本</strong>: mysql:8.0.36 (与生产环境一致)
 *   <li><strong>数据库名</strong>: patra_ingest
 *   <li><strong>用户名/密码</strong>: root / 123456
 *   <li><strong>容器复用</strong>: JVM 内复用，JVM 间不复用（避免配置缓存污染）
 * </ul>
 *
 * <h3>性能表现</h3>
 *
 * <ul>
 *   <li>首次启动: ~20-30 秒（类加载时执行一次）
 *   <li>后续测试: JVM 内复用容器，无需重启（性能提升 ~20-30 秒/测试类）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 * @see ApplicationContextInitializer
 * @see RocketMQContainerInitializer
 */
public class MySQLContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(MySQLContainerInitializer.class);

  /**
   * MySQL 容器单例实例。
   *
   * <p>容器复用策略:
   *
   * <ul>
   *   <li><strong>JVM 内复用</strong>: 通过静态单例模式，同一 JVM 进程内所有测试共享此容器实例
   *   <li><strong>JVM 间不复用</strong>: {@code withReuse(false)} 表示不跨 JVM 进程复用容器
   *   <li><strong>原因</strong>: 避免测试配置缓存污染（不同测试运行可能有不同的配置需求）
   * </ul>
   *
   * <p>容器配置:
   *
   * <ul>
   *   <li><strong>版本</strong>: mysql:8.0.36 (与生产环境一致)
   *   <li><strong>数据库名</strong>: patra_ingest
   *   <li><strong>用户名/密码</strong>: root / 123456
   * </ul>
   */
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

  /**
   * 初始化 Spring 应用上下文，注入 MySQL 动态配置。
   *
   * <p>注入的配置项:
   *
   * <ul>
   *   <li>{@code spring.datasource.url}: JDBC URL (包含动态端口)
   *   <li>{@code spring.datasource.username}: root
   *   <li>{@code spring.datasource.password}: 123456
   *   <li>{@code spring.datasource.driver-class-name}: com.mysql.cj.jdbc.Driver
   * </ul>
   *
   * @param applicationContext Spring 应用上下文
   */
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

  /**
   * 获取 MySQL 容器实例（供测试代码访问）。
   *
   * @return MySQL 容器实例
   */
  public static MySQLContainer<?> getMySQLContainer() {
    return mysql;
  }
}
