package dev.linqibin.starter.batch.autoconfigure;

import dev.linqibin.starter.batch.config.BatchProperties;
import dev.linqibin.starter.batch.core.JobOperatorHelper;
import dev.linqibin.starter.batch.schema.BatchSchemaInitializer;
import io.micrometer.observation.ObservationRegistry;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.BatchObservabilityBeanPostProcessor;
import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/// Spring Batch 自动配置。
///
/// 继承 `JdbcDefaultBatchConfiguration` 以使用基于数据库的 Job 元数据存储。
///
/// ### 配置组件
///
/// - JobRepository - 基于数据库的 Job 元数据存储
/// - JobOperator - Job 启动和运维操作统一接口（Spring Batch 6.0 起替代 JobLauncher）
/// - JobOperatorHelper - 封装 Job 启动逻辑的辅助类
///
/// ### 数据源选择
///
/// 支持独立数据源配置，优先级如下：
///
/// 1. 用户自定义 `batchDataSource` Bean（最高）
/// 2. `linqibin.starter.batch.datasource.*` 配置创建的数据源
/// 3. 主数据源 `@Primary DataSource`（默认回退）
///
/// 条件激活：`linqibin.starter.batch.enabled=true`（默认启用）
///
/// @author Patra Team
/// @since 0.1.0
@ConditionalOnProperty(
    prefix = "linqibin.starter.batch",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, BatchDataSourceConfiguration.class})
@EnableConfigurationProperties(BatchProperties.class)
public class BatchAutoConfiguration extends JdbcDefaultBatchConfiguration {

  private final BatchProperties properties;
  private final DataSource batchDataSource;
  private final PlatformTransactionManager batchTransactionManager;

  /// 构造函数：优先使用独立数据源，否则回退到主数据源。
  ///
  /// 在构造时触发 Schema 初始化（如果配置了 `BatchSchemaInitializer`），
  /// 确保 `JobRepository` 创建前元数据表已存在。
  ///
  /// @param properties Batch 配置属性
  /// @param primaryDataSource 主数据源（Spring Boot 自动配置，使用 `dataSource` 名称注入）
  /// @param batchDataSource 独立 Batch 数据源（可选，由 BatchDataSourceConfiguration 创建）
  /// @param batchTransactionManager 独立 Batch 事务管理器（可选）
  /// @param schemaInitializer Schema 初始化器（可选，由 BatchSchemaInitializerConfiguration 创建）
  public BatchAutoConfiguration(
      BatchProperties properties,
      @Qualifier("dataSource") DataSource primaryDataSource,
      @Autowired(required = false) @Qualifier("batchDataSource") DataSource batchDataSource,
      @Autowired(required = false) @Qualifier("batchTransactionManager")
          PlatformTransactionManager batchTransactionManager,
      @Autowired(required = false) BatchSchemaInitializer schemaInitializer) {
    this.properties = properties;
    // 优先使用独立数据源，否则回退到主数据源
    this.batchDataSource = (batchDataSource != null) ? batchDataSource : primaryDataSource;
    // 优先使用独立事务管理器，否则创建新的
    this.batchTransactionManager =
        (batchTransactionManager != null)
            ? batchTransactionManager
            : new JdbcTransactionManager(this.batchDataSource);

    // 触发 Schema 初始化（幂等，可安全重复调用）
    if (schemaInitializer != null) {
      schemaInitializer.initialize();
    }
  }

  /// 返回 Batch 使用的数据源。
  ///
  /// 重写父类方法，支持独立数据源。
  @Override
  protected DataSource getDataSource() {
    return batchDataSource;
  }

  /// 返回 Batch 使用的事务管理器。
  ///
  /// 重写父类方法，支持独立事务管理器。
  @Override
  protected PlatformTransactionManager getTransactionManager() {
    return batchTransactionManager;
  }

  /// 返回表前缀。
  ///
  /// 从配置属性读取，默认为 `BATCH_`。
  @Override
  protected String getTablePrefix() {
    return properties.getTablePrefix();
  }

  /// 注册 Spring Batch 原生可观测性支持。
  ///
  /// 当 `ObservationRegistry` 存在时，自动将其注入到所有 Job 和 Step Bean 中，
  /// 创建 Job 级别的 trace 和 Step 级别的 span。
  ///
  /// ## 关键点
  ///
  /// - 使用 `static` 方法声明 BeanPostProcessor，避免生命周期问题
  /// - 条件激活：仅在 `ObservationRegistry` Bean 存在时生效
  /// - 零配置：用户无需修改任何 Job 定义代码
  /// - BeanPostProcessor 会自动从 ApplicationContext 获取 ObservationRegistry
  ///
  /// @return BatchObservabilityBeanPostProcessor 实例
  @Bean
  @ConditionalOnBean(ObservationRegistry.class)
  public static BatchObservabilityBeanPostProcessor batchObservabilityBeanPostProcessor() {
    return new BatchObservabilityBeanPostProcessor();
  }

  /// 配置 JobOperatorHelper。
  ///
  /// 封装 JobOperator 调用逻辑，提供强类型参数支持和幂等性控制。
  ///
  /// **Spring Batch 6.0 变更**：
  /// - `JobLauncher` 已弃用，使用 `JobOperator` 替代
  /// - `JobRepository` 现在直接继承 `JobExplorer` 接口，不再需要单独的 `JobExplorer` Bean
  ///
  /// @param jobOperator Job 启动和运维操作接口
  /// @param jobRepository Job 元数据仓库（同时提供 JobExplorer 功能）
  /// @return JobOperatorHelper 实例
  @Bean
  public JobOperatorHelper jobOperatorHelper(JobOperator jobOperator, JobRepository jobRepository) {
    return new JobOperatorHelper(jobOperator, jobRepository);
  }
}
