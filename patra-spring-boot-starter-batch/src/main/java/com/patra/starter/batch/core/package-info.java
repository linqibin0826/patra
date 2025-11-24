///
/// Spring Batch 核心工具包
///
/// ## 职责
///
/// 提供批处理任务的核心辅助工具，简化 Job 启动和执行管理。
///
/// ## 核心组件
///
/// - `JobLauncherHelper`：Job 启动辅助类，封装 JobLauncher 调用逻辑，简化 XXL-Job 触发 Batch 任务的集成
///
/// ## 使用示例
///
/// ### 在 XXL-Job Handler 中启动 Batch Job
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
///         // 启动 Job（每次新实例）
///         Long executionId = jobLauncherHelper.launch(meshImportJob, Map.of(
///             "year", "2024",
///             "source", "nlm"
///         ));
///         log.info("MeSH 导入任务启动，执行 ID: {}", executionId);
///     }
/// }
/// ```
///
/// ### 幂等执行（相同参数只执行一次）
///
/// ```java
/// @XxlJob("dailyReportJob")
/// public void generateDailyReport() {
///     // 启动 Job（幂等执行）
///     Long executionId = jobLauncherHelper.launch(dailyReportJob, Map.of(
///         "date", LocalDate.now().toString()
///     ), false);  // addTimestamp=false，相同 date 只执行一次
/// }
/// ```
///
/// ## JobLauncherHelper 核心方法
///
/// ### launch(Job, Map<String, Object>)
///
/// 启动 Job（默认添加 timestamp，每次创建新实例）
///
/// **参数**：
/// - `job`：Job 实例
/// - `params`：Job 参数（Map）
///
/// **返回**：JobExecution ID
///
/// ### launch(Job, Map<String, Object>, boolean)
///
/// 启动 Job（可选是否添加 timestamp）
///
/// **参数**：
/// - `job`：Job 实例
/// - `params`：Job 参数（Map）
/// - `addTimestamp`：是否添加 timestamp（控制幂等性）
///
/// **返回**：JobExecution ID
///
/// ## 注意事项
///
/// ### timestamp 参数的作用
///
/// Spring Batch 使用 `JobParameters` 唯一标识一个 JobInstance：
/// - **相同参数**：Spring Batch 认为是同一个 JobInstance，如果已完成，不会重复执行
/// - **不同参数**：创建新的 JobInstance，允许执行
///
/// `addTimestamp=true` 会自动添加当前时间戳到 JobParameters，确保每次调用都创建新实例。
///
/// ### 选择策略
///
/// - **可重复执行的任务**（如数据同步、定期清理）→ `addTimestamp=true`
/// - **幂等任务**（如日报生成、按日期分区的导入）→ `addTimestamp=false`
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.batch.core;
