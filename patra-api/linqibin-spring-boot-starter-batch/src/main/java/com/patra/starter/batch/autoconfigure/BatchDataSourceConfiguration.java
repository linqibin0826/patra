package com.patra.starter.batch.autoconfigure;

import com.patra.starter.batch.config.BatchProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

/// Batch 独立数据源自动配置。
///
/// 当配置了 `patra.batch.datasource.url` 时，创建独立的数据源和事务管理器，
/// 用于存储 Spring Batch 元数据（JobRepository、JobExplorer 等）。
///
/// ## 激活条件
///
/// - `patra.batch.datasource.url` 配置了有效值
///
/// ## 创建的 Bean
///
/// - `batchDataSource` - HikariCP 数据源（`defaultCandidate=false`）
/// - `batchTransactionManager` - JDBC 事务管理器（`defaultCandidate=false`）
///
/// ## 多数据源兼容性
///
/// 使用 Spring Boot 3.x 的 `@Bean(defaultCandidate = false)` 特性：
///
/// - Batch 数据源不参与自动装配候选
/// - 不影响 JPA 的 `@ConditionalOnSingleCandidate(DataSource.class)` 检查
/// - 主数据源的自动配置正常工作
/// - 需要 Batch 数据源的地方通过 `@Qualifier("batchDataSource")` 显式注入
///
/// @author Patra Team
/// @since 0.1.0
/// @see <a href="https://docs.spring.io/spring-boot/how-to/data-access.html">Spring Boot Data
// Access</a>
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(HikariDataSource.class)
@ConditionalOnProperty(prefix = "patra.batch.datasource", name = "url")
@EnableConfigurationProperties(BatchProperties.class)
public class BatchDataSourceConfiguration {

  /// 创建 Batch 专用数据源。
  ///
  /// 使用 HikariCP 连接池，配置来自 `patra.batch.datasource.*`。
  ///
  /// **注意**：`defaultCandidate = false` 确保此数据源不参与自动装配候选，
  /// 避免干扰 JPA 的 `@ConditionalOnSingleCandidate(DataSource.class)` 检查。
  ///
  /// @param properties Batch 配置属性
  /// @return HikariDataSource 实例
  @Bean(defaultCandidate = false)
  @ConditionalOnMissingBean(name = "batchDataSource")
  public DataSource batchDataSource(BatchProperties properties) {
    var dsProps = properties.getDatasource();
    var hikariProps = dsProps.getHikari();

    var config = new HikariConfig();
    config.setJdbcUrl(dsProps.getUrl());
    config.setUsername(dsProps.getUsername());
    config.setPassword(dsProps.getPassword());

    if (StringUtils.hasText(dsProps.getDriverClassName())) {
      config.setDriverClassName(dsProps.getDriverClassName());
    }

    config.setMaximumPoolSize(hikariProps.getMaximumPoolSize());
    config.setMinimumIdle(hikariProps.getMinimumIdle());
    config.setConnectionTimeout(hikariProps.getConnectionTimeout());
    config.setIdleTimeout(hikariProps.getIdleTimeout());
    config.setMaxLifetime(hikariProps.getMaxLifetime());
    config.setPoolName("batch-hikari-pool");

    return new HikariDataSource(config);
  }

  /// 创建 Batch 专用事务管理器。
  ///
  /// 与 `batchDataSource` 绑定，用于管理 Spring Batch 元数据表的事务。
  ///
  /// **注意**：`defaultCandidate = false` 确保此事务管理器不参与自动装配候选，
  /// 避免干扰 JPA 的事务管理器自动配置。
  ///
  /// @param batchDataSource Batch 专用数据源
  /// @return JdbcTransactionManager 实例
  @Bean(defaultCandidate = false)
  @ConditionalOnMissingBean(name = "batchTransactionManager")
  public PlatformTransactionManager batchTransactionManager(
      @Qualifier("batchDataSource") DataSource batchDataSource) {
    return new JdbcTransactionManager(batchDataSource);
  }
}
