///
/// Spring Batch 监听器包
///
/// ## 职责
///
/// 提供 Spring Batch Job/Step 的生命周期监听器，支持日志记录、可观测性集成（追踪、指标）。
///
/// ## 核心组件
///
/// - `LoggingJobListener`：日志记录监听器（基础设施层调试工具，记录 Job/Step 执行日志）
/// - `SkyWalkingJobListener`：SkyWalking 追踪监听器（可选，需依赖 patra-spring-boot-starter-observability）
/// - `MetricsJobListener`：Micrometer 指标监听器（可选，需依赖 patra-spring-boot-starter-observability）
///
/// ## 使用示例
///
/// ### 自动注册（默认行为）
///
/// 添加 `patra-spring-boot-starter-batch` 依赖后，`LoggingJobListener` 会自动注册到所有 Job。
///
/// ### 手动注册监听器到特定 Job
///
/// ```java
/// @Configuration
/// @RequiredArgsConstructor
/// public class MeshImportJobConfig {
///
///     private final LoggingJobListener loggingJobListener;
///
///     @Bean
///     public Job meshImportJob(JobRepository jobRepository, Step importStep) {
///         return new JobBuilder("meshImportJob", jobRepository)
///             .listener(loggingJobListener)  // 手动添加监听器
///             .start(importStep)
///             .build();
///     }
/// }
/// ```
///
/// ### 自定义监听器
///
/// 实现 `JobExecutionListener` 接口：
///
/// ```java
/// @Component
/// public class CustomJobListener implements JobExecutionListener {
///
///     @Override
///     public void beforeJob(JobExecution jobExecution) {
///         // Job 启动前逻辑
///         log.info("Job [{}] 启动", jobExecution.getJobInstance().getJobName());
///     }
///
///     @Override
///     public void afterJob(JobExecution jobExecution) {
///         // Job 完成后逻辑
///         if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
///             log.info("Job [{}] 成功完成", jobExecution.getJobInstance().getJobName());
///         } else {
///             log.error("Job [{}] 失败", jobExecution.getJobInstance().getJobName());
///         }
///     }
/// }
/// ```
///
/// ## 监听器执行顺序
///
/// 当多个监听器注册到同一个 Job 时，执行顺序由 `@Order` 注解控制（数值越小优先级越高）：
///
/// 1. **SkyWalkingJobListener**（@Order(10)）：创建追踪 Span
/// 2. **MetricsJobListener**（@Order(20)）：收集执行指标
/// 3. **LoggingJobListener**（@Order(100)）：记录日志
///
/// ## LoggingJobListener 日志格式
///
/// ### Job 启动日志
///
/// ```
/// INFO  Job [meshImportJob] 启动，执行 ID: 12345
/// ```
///
/// ### Job 完成日志（成功）
///
/// ```
/// INFO  Job [meshImportJob] 成功完成，执行 ID: 12345，耗时: 1h 23m 45s
/// ```
///
/// ### Job 完成日志（失败）
///
/// ```
/// ERROR Job [meshImportJob] 失败，执行 ID: 12345，耗时: 5m 30s，错误: Connection timeout
/// ```
///
/// ## 可观测性集成
///
/// 添加 `patra-spring-boot-starter-observability` 依赖后，以下监听器自动启用：
///
/// ### SkyWalkingJobListener
///
/// - 创建追踪 Span：`Batch.Job.{jobName}`
/// - 记录 Job 执行时长、成功/失败状态
/// - 传播 TraceID 到 Batch 上下文
///
/// ### MetricsJobListener
///
/// - 收集指标：
///   - `batch.job.executions`（Counter）：Job 执行次数
///   - `batch.job.duration`（Timer）：Job 执行时长
///   - `batch.job.failures`（Counter）：Job 失败次数
/// - 标签（Tags）：
///   - `job_name`：Job 名称
///   - `status`：执行状态（COMPLETED、FAILED、STOPPED）
///
/// ## 注意事项
///
/// ### LoggingJobListener 不属于可观测性范畴
///
/// `LoggingJobListener` 是基础设施层的调试工具，记录结构化日志用于开发和故障排查，
/// 与可观测性（Observability）的追踪（Tracing）、指标（Metrics）不同：
///
/// - **LoggingJobListener**：开发调试、故障排查（保留在本 Starter）
/// - **SkyWalkingJobListener、MetricsJobListener**：生产监控、性能分析（在 observability Starter）
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.batch.listener;
