package com.patra.starter.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/// {@link BatchDataSourceConfiguration} 单元测试。
///
/// 验证 Batch 独立数据源的条件装配逻辑。
/// 使用 MySQL TestContainers 确保与生产环境一致。
@Testcontainers
class BatchDataSourceConfigurationTest {

  /// MySQL 容器（JVM 级别共享）。
  @Container
  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("batch_test")
          .withUsername("root")
          .withPassword("123456");

  private static String jdbcUrl;
  private static String username;
  private static String password;

  @BeforeAll
  static void setupDatabase() {
    jdbcUrl = MYSQL.getJdbcUrl();
    username = MYSQL.getUsername();
    password = MYSQL.getPassword();
  }

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BatchDataSourceConfiguration.class));

  @Test
  @DisplayName("未配置 datasource.url 时：不应创建 batchDataSource Bean")
  void batchDataSource_ShouldNotBeCreated_WhenUrlNotConfigured() {
    // When: 没有配置 datasource.url
    contextRunner.run(
        context -> {
          // Then: 不应创建 batchDataSource Bean
          assertThat(context).doesNotHaveBean("batchDataSource");
          assertThat(context).doesNotHaveBean("batchTransactionManager");
        });
  }

  @Test
  @DisplayName("配置 datasource.url 时：应创建 batchDataSource 和 batchTransactionManager Bean")
  void batchDataSource_ShouldBeCreated_WhenUrlConfigured() {
    // When: 配置了 datasource.url（使用 MySQL TestContainers）
    contextRunner
        .withPropertyValues(
            "patra.batch.datasource.url=" + jdbcUrl,
            "patra.batch.datasource.username=" + username,
            "patra.batch.datasource.password=" + password)
        .run(
            context -> {
              // Then: 应创建 batchDataSource 和 batchTransactionManager Bean
              assertThat(context).hasBean("batchDataSource");
              assertThat(context).hasBean("batchTransactionManager");

              DataSource dataSource = context.getBean("batchDataSource", DataSource.class);
              assertThat(dataSource).isInstanceOf(HikariDataSource.class);

              PlatformTransactionManager txManager =
                  context.getBean("batchTransactionManager", PlatformTransactionManager.class);
              assertThat(txManager).isNotNull();
            });
  }

  @Test
  @DisplayName("未配置 Hikari 参数时：应使用默认 Hikari 配置")
  void batchDataSource_ShouldUseHikariDefaults() {
    // When: 只配置 URL，不配置 Hikari 参数（使用 MySQL TestContainers）
    contextRunner
        .withPropertyValues(
            "patra.batch.datasource.url=" + jdbcUrl,
            "patra.batch.datasource.username=" + username,
            "patra.batch.datasource.password=" + password)
        .run(
            context -> {
              HikariDataSource dataSource =
                  context.getBean("batchDataSource", HikariDataSource.class);

              // Then: 应使用默认的 Hikari 配置
              assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
              assertThat(dataSource.getMinimumIdle()).isEqualTo(2);
              assertThat(dataSource.getConnectionTimeout()).isEqualTo(30000L);
              assertThat(dataSource.getIdleTimeout()).isEqualTo(600000L);
              assertThat(dataSource.getPoolName()).isEqualTo("batch-hikari-pool");
            });
  }

  @Test
  @DisplayName("配置自定义 Hikari 参数时：应使用自定义配置")
  void batchDataSource_ShouldUseCustomHikariConfig() {
    // When: 配置了自定义 Hikari 参数（使用 MySQL TestContainers）
    contextRunner
        .withPropertyValues(
            "patra.batch.datasource.url=" + jdbcUrl,
            "patra.batch.datasource.username=" + username,
            "patra.batch.datasource.password=" + password,
            "patra.batch.datasource.hikari.maximum-pool-size=10",
            "patra.batch.datasource.hikari.minimum-idle=3",
            "patra.batch.datasource.hikari.connection-timeout=60000",
            "patra.batch.datasource.hikari.idle-timeout=300000")
        .run(
            context -> {
              HikariDataSource dataSource =
                  context.getBean("batchDataSource", HikariDataSource.class);

              // Then: 应使用自定义配置
              assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
              assertThat(dataSource.getMinimumIdle()).isEqualTo(3);
              assertThat(dataSource.getConnectionTimeout()).isEqualTo(60000L);
              assertThat(dataSource.getIdleTimeout()).isEqualTo(300000L);
            });
  }

  @Test
  @DisplayName("用户自定义 batchDataSource Bean 时：不应覆盖用户定义的 Bean")
  void batchDataSource_ShouldNotOverrideUserDefinedBean() {
    // Given: 用户自定义了 batchDataSource Bean（使用 MySQL TestContainers）
    contextRunner
        .withPropertyValues(
            "patra.batch.datasource.url=" + jdbcUrl,
            "patra.batch.datasource.username=" + username,
            "patra.batch.datasource.password=" + password)
        .withBean(
            "batchDataSource",
            DataSource.class,
            () -> {
              HikariDataSource ds = new HikariDataSource();
              ds.setJdbcUrl(jdbcUrl);
              ds.setUsername(username);
              ds.setPassword(password);
              ds.setPoolName("user-defined-pool");
              return ds;
            })
        .run(
            context -> {
              // Then: 应使用用户定义的 Bean
              HikariDataSource dataSource =
                  context.getBean("batchDataSource", HikariDataSource.class);
              assertThat(dataSource.getPoolName()).isEqualTo("user-defined-pool");
            });
  }
}
