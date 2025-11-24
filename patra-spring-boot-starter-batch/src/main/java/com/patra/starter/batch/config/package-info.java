///
/// Spring Batch 配置属性包
///
/// ## 职责
///
/// 定义 Spring Batch Starter 的配置属性类，支持通过 `application.yml` 自定义批处理行为。
///
/// ## 核心组件
///
/// - `BatchProperties`：批处理配置属性类（配置前缀：`patra.batch`）
///
/// ## 配置示例
///
/// ```yaml
/// patra:
///   batch:
///     enabled: true  # 是否启用批处理自动配置
///
///     # 可观测性配置
///     observability:
///       tracing:
///         enabled: true  # SkyWalking 追踪
///       metrics:
///         enabled: true  # Micrometer 指标
///       logging:
///         enabled: true  # 结构化日志
///
///     # Chunk 配置
///     chunk:
///       default-size: 1000  # 默认批次大小
///       max-size: 10000     # 最大批次大小
/// ```
///
/// ## BatchProperties 属性说明
///
/// ### enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用 Spring Batch 自动配置
///
/// ### observability.tracing.enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用 SkyWalking 追踪集成（需依赖 patra-spring-boot-starter-observability）
///
/// ### observability.metrics.enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用 Micrometer 指标集成（需依赖 patra-spring-boot-starter-observability）
///
/// ### observability.logging.enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用结构化日志记录（LoggingJobListener）
///
/// ### chunk.default-size
///
/// **类型**：`int`
/// **默认值**：`1000`
/// **说明**：默认 Chunk 批次大小（建议值：100-5000）
///
/// ### chunk.max-size
///
/// **类型**：`int`
/// **默认值**：`10000`
/// **说明**：最大 Chunk 批次大小（防止内存溢出）
///
/// ## 使用示例
///
/// ### 在代码中读取配置
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class JobConfigurer {
///
///     private final BatchProperties batchProperties;
///
///     @Bean
///     public Step importStep(
///         JobRepository jobRepository,
///         PlatformTransactionManager transactionManager
///     ) {
///         int chunkSize = batchProperties.getChunk().getDefaultSize();  // 读取配置
///         return new StepBuilder("importStep", jobRepository)
///             .<Source, Target>chunk(chunkSize, transactionManager)
///             .reader(reader())
///             .processor(processor())
///             .writer(writer())
///             .build();
///     }
/// }
/// ```
///
/// ### 环境特定配置
///
/// ```yaml
/// # application-dev.yml
/// patra:
///   batch:
///     chunk:
///       default-size: 100  # 开发环境小批次便于调试
///
/// # application-prod.yml
/// patra:
///   batch:
///     chunk:
///       default-size: 5000  # 生产环境大批次提升性能
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.batch.config;
