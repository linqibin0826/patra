package com.patra.starter.batch.autoconfigure;

import com.patra.starter.batch.config.BatchProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
/// - `batchDataSource` - HikariCP 数据源
/// - `batchTransactionManager` - JDBC 事务管理器
///
/// @author Patra Team
/// @since 1.0.0
@Configuration
@ConditionalOnProperty(prefix = "patra.batch.datasource", name = "url")
@EnableConfigurationProperties(BatchProperties.class)
public class BatchDataSourceConfiguration {

  /// 创建 Batch 专用数据源。
  ///
  /// 使用 HikariCP 连接池，配置来自 `patra.batch.datasource.*`。
  ///
  /// @param properties Batch 配置属性
  /// @return HikariDataSource 实例
  @Bean
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
    config.setPoolName("batch-hikari-pool");

    return new HikariDataSource(config);
  }

  /// 创建 Batch 专用事务管理器。
  ///
  /// 与 `batchDataSource` 绑定，用于管理 Spring Batch 元数据表的事务。
  ///
  /// @param batchDataSource Batch 专用数据源
  /// @return JdbcTransactionManager 实例
  @Bean
  @ConditionalOnMissingBean(name = "batchTransactionManager")
  public PlatformTransactionManager batchTransactionManager(
      @Qualifier("batchDataSource") DataSource batchDataSource) {
    return new JdbcTransactionManager(batchDataSource);
  }
}
