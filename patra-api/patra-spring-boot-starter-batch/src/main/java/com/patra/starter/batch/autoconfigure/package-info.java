///
/// Spring Batch 自动配置包
///
/// ## 职责
///
/// 提供 Spring Batch 的自动配置类，根据条件自动激活批处理能力和可观测性集成。
///
/// ## 核心组件
///
/// - `BatchAutoConfiguration`：Spring Batch 核心组件自动配置（JobRepository、JobLauncher、JobExplorer、JobOperator）
/// - `ObservabilityAutoConfiguration`：可观测性自动配置（条件激活：依赖 patra-spring-boot-starter-observability）
///
/// ## 自动配置机制
///
/// ### BatchAutoConfiguration
///
/// **激活条件**：`patra.batch.enabled=true`（默认启用）
///
/// **配置内容**：
/// - `JobRepository`：基于数据库的 Job 元数据存储
/// - `JobLauncher`：同步 Job 启动器（使用 SyncTaskExecutor）
/// - `JobExplorer`：Job 执行历史查询
/// - `JobOperator`：Job 运维操作接口
/// - `LoggingJobListener`：日志监听器（记录 Job/Step 执行日志）
///
/// ### ObservabilityAutoConfiguration
///
/// **激活条件**：
/// - `patra.batch.observability.enabled=true`（默认启用）
/// - 依赖 `patra-spring-boot-starter-observability`
///
/// **配置内容**：
/// - `SkyWalkingJobListener`：SkyWalking 追踪监听器
/// - `MetricsJobListener`：Micrometer 指标监听器
///
/// ## 使用示例
///
/// ### 自定义配置
///
/// ```yaml
/// patra:
///   batch:
///     enabled: true  # 启用 Spring Batch
///     observability:
///       tracing:
///         enabled: true  # 启用 SkyWalking 追踪
///       metrics:
///         enabled: true  # 启用 Micrometer 指标
///       logging:
///         enabled: true  # 启用日志记录
/// ```
///
/// ### 禁用自动配置
///
/// ```yaml
/// patra:
///   batch:
///     enabled: false  # 禁用 Spring Batch 自动配置
/// ```
///
/// ## 扩展 Spring Batch 配置
///
/// 如果需要自定义 JobRepository 或 JobLauncher，可以覆盖默认 Bean：
///
/// ```java
/// @Configuration
/// public class CustomBatchConfig {
///
///     @Bean
///     @Primary
///     public JobRepository customJobRepository(
///         DataSource dataSource,
///         PlatformTransactionManager transactionManager
///     ) throws Exception {
///         JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
///         factory.setDataSource(dataSource);
///         factory.setTransactionManager(transactionManager);
///         factory.setTablePrefix("CUSTOM_BATCH_");  // 自定义表前缀
///         factory.setIsolationLevelForCreate("ISOLATION_SERIALIZABLE");
///         factory.afterPropertiesSet();
///         return factory.getObject();
///     }
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.batch.autoconfigure;
