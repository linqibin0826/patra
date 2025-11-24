///
/// Spring Batch 批处理基础设施 Starter
///
/// ## 职责
///
/// 提供标准化的 Spring Batch 批处理能力，为 Patra 医学文献数据平台的批量数据处理任务提供统一的技术支撑。
///
/// **核心功能**：
/// - Spring Batch 核心组件自动配置（JobRepository、JobLauncher、JobExplorer、JobOperator）
/// - 集成分布式锁（patra-spring-boot-starter-redisson）防止任务并发执行
/// - 提供 JobLauncherHelper 简化 XXL-Job 触发 Batch 任务的集成
/// - 支持断点续传（基于 Spring Batch JobRepository）
/// - 支持 Chunk 批次处理和重试、跳过策略
/// - 可选的可观测性集成（添加 patra-spring-boot-starter-observability 自动启用）
///
/// ## 核心组件
///
/// - `BatchAutoConfiguration`：Spring Batch 自动配置类
/// - `JobLauncherHelper`：Job 启动辅助工具，封装 JobLauncher 调用
/// - `LoggingJobListener`：日志监听器，记录 Job/Step 执行日志
/// - `ObservabilityAutoConfiguration`：可观测性自动配置（条件激活）
/// - `BatchProperties`：配置属性类（patra.batch.*）
///
/// ## 使用示例
///
/// ### 1. 添加依赖
///
/// ```xml
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-spring-boot-starter-batch</artifactId>
/// </dependency>
/// ```
///
/// ### 2. 配置数据源
///
/// ```yaml
/// spring:
///   datasource:
///     url: jdbc:mysql://localhost:3306/patra_catalog
///     username: root
///     password: password
///   batch:
///     jdbc:
///       initialize-schema: always  # 自动创建 Spring Batch 元数据表
/// ```
///
/// ### 3. 定义 Job
///
/// ```java
/// @Configuration
/// public class MeshImportJobConfig {
///     @Bean
///     public Job meshImportJob(JobRepository jobRepository, Step importStep) {
///         return new JobBuilder("meshImportJob", jobRepository)
///             .start(importStep)
///             .build();
///     }
///
///     @Bean
///     public Step importStep(
///             JobRepository jobRepository,
///             PlatformTransactionManager transactionManager,
///             ItemReader<MeshDescriptor> reader,
///             ItemProcessor<MeshDescriptor, MeshDescriptorDO> processor,
///             ItemWriter<MeshDescriptorDO> writer
///     ) {
///         return new StepBuilder("importStep", jobRepository)
///             .<MeshDescriptor, MeshDescriptorDO>chunk(1000, transactionManager)
///             .reader(reader)
///             .processor(processor)
///             .writer(writer)
///             .build();
///     }
/// }
/// ```
///
/// ### 4. 通过 XXL-Job 触发
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class MeshImportJobHandler {
///
///     private final JobLauncherHelper jobLauncherHelper;
///     private final Job meshImportJob;
///
///     @XxlJob("meshImportJob")
///     @DistributedLock(key = "batch:job:mesh-import", leaseTime = 7200000)
///     public void execute() {
///         jobLauncherHelper.launch(meshImportJob, Map.of("year", "2024"));
///     }
/// }
/// ```
///
/// ## 架构位置
///
/// 在六边形架构中，本 Starter 位于**框架层（Framework Layer）**，为基础设施层（Infrastructure Layer）
/// 提供批处理技术支撑。
///
/// ```
/// Boot 层 (patra-xxx-boot)
///   - XXL-Job Handler（定时触发）
///     ↓ uses
/// Framework 层 (patra-starter-batch) ← 本 Starter
///   - JobLauncherHelper（启动 Job）
///   - JobRepository（管理执行状态）
///     ↓ calls
/// Infrastructure 层 (patra-xxx-infra)
///   - ItemReader（读取数据）
///   - ItemProcessor（转换数据）
///   - ItemWriter（写入数据）
/// ```
///
/// ## 依赖关系
///
/// - `spring-boot-starter-batch`：Spring Batch 核心
/// - `patra-spring-boot-starter-core`：错误处理、可观测性集成
/// - `patra-spring-boot-starter-redisson`：分布式锁支持（可选）
/// - `patra-spring-boot-starter-observability`：追踪、指标、日志（可选）
///
/// ## 注意事项
///
/// ### JobLauncher 同步执行
///
/// 本 Starter 的 JobLauncher 使用 `SyncTaskExecutor`（同步执行），而非 `SimpleAsyncTaskExecutor`（异步执行）。
///
/// **原因**：XXL-Job 已经提供异步调度能力，无需 Spring Batch 二次异步，避免以下问题：
/// - XXL-Job 立即返回成功，但 Batch 任务可能尚未完成
/// - 无法正确传播异常到 XXL-Job 控制台
/// - 分布式锁提前释放（方法返回 != 任务完成）
///
/// ### 与 @Transactional 的兼容性
///
/// Spring Batch 的 Chunk 处理自带事务管理，建议在 ItemReader/ItemWriter 中避免使用 `@Transactional`，
/// 而是通过 StepBuilder 的 `transactionManager` 参数统一管理事务。
///
/// ### 分布式锁推荐配置
///
/// 使用 `@DistributedLock` 防止 Batch 任务并发执行时，`leaseTime` 应设置为业务执行时间的 2-3 倍：
///
/// ```java
/// @DistributedLock(
///     key = "batch:job:mesh-import",
///     leaseTime = 7200000  // 2 小时（MeSH 导入预计 30-60 分钟）
/// )
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.batch;
