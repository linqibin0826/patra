package com.patra.starter.batch.autoconfigure;

import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.schema.BatchSchemaInitializer;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// Spring Batch Schema 初始化自动配置。
///
/// 当 `patra.batch.schema.initialize=true`（默认）时，自动创建 `BatchSchemaInitializer` Bean，
/// 在 `BatchAutoConfiguration` 构造时触发 Schema 初始化。
///
/// ## 配置选项
///
/// ```yaml
/// patra:
///   batch:
///     schema:
///       initialize: true  # 默认启用
/// ```
///
/// ## 禁用场景
///
/// - 使用 Flyway/Liquibase 管理 Schema 迁移
/// - 多服务共享数据库，仅由指定服务初始化
/// - 使用预配置的数据库（Schema 已存在）
///
/// ## 加载顺序
///
/// 此配置类在 `BatchDataSourceConfiguration` 之后、`BatchAutoConfiguration` 之前加载，
/// 确保数据源就绪后再初始化 Schema。
///
/// @author Patra Team
/// @since 0.1.0
@ConditionalOnProperty(
    prefix = "patra.batch.schema",
    name = "initialize",
    havingValue = "true",
    matchIfMissing = true)
@AutoConfiguration(
    after = BatchDataSourceConfiguration.class,
    before = BatchAutoConfiguration.class)
@EnableConfigurationProperties(BatchProperties.class)
public class BatchSchemaInitializerConfiguration {

  /// 创建 Schema 初始化器 Bean。
  ///
  /// 优先使用独立 Batch 数据源，否则使用主数据源。
  ///
  /// @param properties Batch 配置属性
  /// @param primaryDataSource 主数据源
  /// @param batchDataSource 独立 Batch 数据源（可选）
  /// @return BatchSchemaInitializer 实例
  @Bean
  public BatchSchemaInitializer batchSchemaInitializer(
      BatchProperties properties,
      DataSource primaryDataSource,
      @Autowired(required = false) @Qualifier("batchDataSource") DataSource batchDataSource) {

    // 选择数据源：优先使用独立数据源
    DataSource targetDataSource = (batchDataSource != null) ? batchDataSource : primaryDataSource;

    return new BatchSchemaInitializer(targetDataSource, properties.getTablePrefix());
  }
}
