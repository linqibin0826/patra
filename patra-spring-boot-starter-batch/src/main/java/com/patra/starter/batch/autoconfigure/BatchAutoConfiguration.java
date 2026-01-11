package com.patra.starter.batch.autoconfigure;

import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.core.JobLauncherHelper;
import com.patra.starter.batch.schema.BatchSchemaInitializer;
import io.micrometer.observation.ObservationRegistry;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.BatchObservabilityBeanPostProcessor;
// Spring Batch 6.0: 使用 JdbcDefaultBatchConfiguration 替代 DefaultBatchConfiguration
import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// Spring Boot 4.0: JDBC 自动配置类迁移到新包
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/// Spring Batch 自动配置。
///
/// **Spring Batch 6.0 变更**：继承 `JdbcDefaultBatchConfiguration` 而非 `DefaultBatchConfiguration`，
/// 因为 6.0 版本中 `DefaultBatchConfiguration` 默认使用 `ResourcelessJobRepository`。
///
/// 自动配置 Spring Batch 核心组件：
///
/// - JobRepository - 基于数据库的 Job 元数据存储（继承 JobExplorer 接口）
/// - JobLauncher - Job 启动器（使用 SyncTaskExecutor，XXL-Job 已异步）
/// - JobOperator - Job 运维操作接口
///
/// ## 数据源选择
///
/// 支持独立数据源配置，优先级如下：
///
/// 1. 用户自定义 `batchDataSource` Bean（最高）
/// 2. `patra.batch.datasource.*` 配置创建的数据源
/// 3. 主数据源 `@Primary DataSource`（默认回退）
///
/// ## 关键改进
///
/// - JobLauncher 使用 `SyncTaskExecutor`（XXL-Job 已异步）
/// - 移除 `@EnableBatchProcessing`（Spring Boot 3 推荐）
///
/// 条件激活：`patra.batch.enabled=true`（默认启用）
///
/// @author Patra Team
/// @since 0.1.0
@ConditionalOnProperty(
    prefix = "patra.batch",
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

  /// 配置 JobLauncher（同步执行）。
  ///
  /// **Spring Batch 6.0 变更**：`JobLauncher` 接口已废弃（`JobOperator` 现在继承它），
  /// 但实现类 `TaskExecutorJobLauncher` 仍可使用。
  ///
  /// ## 关键改进
  ///
  /// 使用 `SyncTaskExecutor` 替代 `SimpleAsyncTaskExecutor`。
  ///
  /// ## 原因
  ///
  /// - XXL-Job 已提供异步调度能力（线程池执行）
  /// - Spring Batch 二次异步会导致 XXL-Job 无法正确追踪执行状态
  /// - 同步执行可确保分布式锁在 Job 执行期间有效
  ///
  /// ## 迁移计划（中期）
  ///
  /// Spring Batch 6.0 推荐使用 `JobOperator` 替代 `JobLauncher`：
  ///
  /// - `JobOperator` 现在继承 `JobLauncher` 接口，提供向后兼容
  /// - 新增功能：`start(Job, JobParameters)`、`restart(long)`、`stop(long)` 等
  /// - 迁移时机：当 `JobLauncherHelper` 需要新增运维功能（如重启、停止）时
  /// - 迁移方式：将 `JobLauncher` 替换为 `JobOperator`，无需修改调用代码
  ///
  /// ```java
  /// // 迁移后的代码示例
  /// @Bean
  /// public JobOperator jobOperator(JobRepository jobRepository) {
  ///     TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
  ///     launcher.setJobRepository(jobRepository);
  ///     launcher.setTaskExecutor(new SyncTaskExecutor());
  ///     launcher.afterPropertiesSet();
  ///     return launcher;  // TaskExecutorJobLauncher 实现 JobOperator
  /// }
  /// ```
  ///
  /// @param jobRepository Job 元数据仓库
  /// @return JobLauncher 实例
  @Bean
  @SuppressWarnings("removal") // JobLauncher 在 Spring Batch 6.0 中废弃，计划中期迁移到 JobOperator
  public JobLauncher jobLauncher(JobRepository jobRepository) {
    TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(jobRepository);

    launcher.setTaskExecutor(new SyncTaskExecutor());

    try {
      launcher.afterPropertiesSet();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize JobLauncher", e);
    }

    return launcher;
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

  /// 配置 JobLauncherHelper。
  ///
  /// 封装 JobLauncher 调用逻辑，提供强类型参数支持和幂等性控制。
  ///
  /// **Spring Batch 6.0 变更**：`JobRepository` 现在直接继承 `JobExplorer` 接口，
  /// 不再需要单独的 `JobExplorer` Bean。
  ///
  /// @param jobLauncher Job 启动器
  /// @param jobRepository Job 元数据仓库（同时提供 JobExplorer 功能）
  /// @return JobLauncherHelper 实例
  @Bean
  public JobLauncherHelper jobLauncherHelper(JobLauncher jobLauncher, JobRepository jobRepository) {
    return new JobLauncherHelper(jobLauncher, jobRepository);
  }
}
