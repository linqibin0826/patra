///
/// Spring Batch 异常包
///
/// ## 职责
///
/// 定义批处理任务的异常类型和错误码，集成 patra-common-core 的统一异常体系。
///
/// ## 核心组件
///
/// - `BatchJobExecutionException`：批处理任务执行异常
/// - `BatchErrorCode`：批处理错误码枚举（实现 ErrorTrait）
///
/// ## 异常层次
///
/// ```
/// InfrastructureException (patra-common-core)
///   └─ BatchJobExecutionException (本包)
/// ```
///
/// ## 使用示例
///
/// ### 抛出批处理异常
///
/// ```java
/// @Component
/// public class MeshImportJobHandler {
///
///     @XxlJob("meshImportJob")
///     public void execute() {
///         try {
///             jobLauncherHelper.launch(meshImportJob, params);
///         } catch (Exception e) {
///             throw new BatchJobExecutionException(
///                 BatchErrorCode.JOB_LAUNCH_FAILED,
///                 "MeSH 导入任务启动失败",
///                 e
///             );
///         }
///     }
/// }
/// ```
///
/// ### 错误码定义
///
/// `BatchErrorCode` 枚举：
///
/// - `JOB_LAUNCH_FAILED`：Job 启动失败
/// - `JOB_EXECUTION_FAILED`：Job 执行失败
/// - `JOB_RESTART_FAILED`：Job 重启失败
/// - `INVALID_JOB_PARAMETERS`：无效的 Job 参数
/// - `CHUNK_PROCESSING_FAILED`：Chunk 处理失败
///
/// ### 异常处理
///
/// 集成 patra-spring-boot-starter-core 的全局异常处理器后，批处理异常会自动转换为统一的错误响应：
///
/// ```json
/// {
///   "code": "BATCH_JOB_LAUNCH_FAILED",
///   "message": "MeSH 导入任务启动失败",
///   "timestamp": "2025-11-24T10:30:00Z",
///   "traceId": "abc123"
/// }
/// ```
///
/// ## BatchErrorCode 枚举说明
///
/// ### JOB_LAUNCH_FAILED
///
/// **HTTP 状态码**：500
/// **说明**：Job 启动失败（通常是配置错误或依赖不可用）
///
/// ### JOB_EXECUTION_FAILED
///
/// **HTTP 状态码**：500
/// **说明**：Job 执行失败（业务逻辑异常、数据库异常等）
///
/// ### JOB_RESTART_FAILED
///
/// **HTTP 状态码**：500
/// **说明**：Job 重启失败（通常是 JobRepository 状态异常）
///
/// ### INVALID_JOB_PARAMETERS
///
/// **HTTP 状态码**：400
/// **说明**：无效的 Job 参数（参数缺失、类型错误、值非法等）
///
/// ### CHUNK_PROCESSING_FAILED
///
/// **HTTP 状态码**：500
/// **说明**：Chunk 处理失败（Reader/Processor/Writer 异常）
///
/// ## 注意事项
///
/// ### 与 Spring Batch 原生异常的关系
///
/// Spring Batch 有自己的异常体系（`JobExecutionException`、`ItemStreamException` 等），
/// `BatchJobExecutionException` 是对这些异常的包装，符合 Patra 统一异常体系：
///
/// ```java
/// try {
///     jobLauncher.run(job, parameters);
/// } catch (JobExecutionException e) {
///     // 包装为 BatchJobExecutionException
///     throw new BatchJobExecutionException(
///         BatchErrorCode.JOB_EXECUTION_FAILED,
///         e.getMessage(),
///         e
///     );
/// }
/// ```
///
/// ### 错误追踪
///
/// 集成 patra-spring-boot-starter-observability 后，异常会自动关联 SkyWalking TraceID，
/// 便于在分布式追踪系统中定位问题。
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.batch.exception;
