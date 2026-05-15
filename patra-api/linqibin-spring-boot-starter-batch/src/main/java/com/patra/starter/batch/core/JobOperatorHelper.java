package com.patra.starter.batch.core;

import com.patra.common.json.JsonMapperHolder;
import com.patra.starter.batch.exception.BatchJobExecutionException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import tools.jackson.databind.ObjectMapper;

/// Job 启动辅助类。
///
/// 封装 Spring Batch JobOperator 调用逻辑，简化批处理任务启动。
///
/// ## Spring Batch 6.0 迁移说明
///
/// - `JobLauncher` 自 Spring Batch 6.0 起已弃用，将在 6.2+ 版本移除
/// - `JobOperator` 现在扩展 `JobLauncher` 接口，两者合并
/// - `JobOperator.start(Job, JobParameters)` 等效于原来的 `JobLauncher.run()`
///
/// ## 强类型参数支持
///
/// 支持强类型的 `JobParams` 参数对象，通过 Jackson 自动转换为 `JobParameters`：
///
/// ```java
/// @Data
/// @Builder
/// public class MeshImportJobParams implements JobParams {
///     private String filePath;
///     private String meshVersion;
/// }
///
/// // 使用示例
/// var params = MeshImportJobParams.builder()
///     .filePath("/path/to/mesh.xml")
///     .meshVersion("2025")
///     .build();
/// jobOperatorHelper.launch(meshImportJob, params);
/// ```
///
/// ## timestamp 参数控制幂等性
///
/// - `addTimestamp=true`: 每次执行都创建新 JobInstance（适用于可重复执行的任务）
/// - `addTimestamp=false`: 相同参数的 Job 只执行一次（幂等性保证）
///
/// @author linqibin
/// @since 0.1.0
@RequiredArgsConstructor
@Slf4j
public class JobOperatorHelper {

  private final JobOperator jobOperator;
  /// Spring Batch 6.0: JobRepository 继承 JobExplorer，提供统一的查询和持久化接口。
  private final JobRepository jobRepository;

  /// 启动 Job（默认添加 timestamp，每次创建新实例）。
  ///
  /// @param job Job 实例
  /// @param params Job 参数（强类型）
  /// @return JobExecution ID
  public Long launch(Job job, JobParams params) {
    return launch(job, params, true);
  }

  /// 启动 Job。
  ///
  /// @param job Job 实例
  /// @param params Job 参数（强类型）
  /// @param addTimestamp 是否添加时间戳
  ///     - `true`: 每次执行都创建新 JobInstance（适用于可重复执行的任务）
  ///     - `false`: 相同参数的 Job 只执行一次（幂等性保证）
  /// @return JobExecution ID
  /// @throws BatchJobExecutionException Job 启动失败时抛出
  public Long launch(Job job, JobParams params, boolean addTimestamp) {
    try {
      Map<String, Object> paramsMap = convertToMap(params);
      Set<String> nonIdentifyingKeys = params.getNonIdentifyingKeys();
      JobParameters jobParameters = buildJobParameters(paramsMap, nonIdentifyingKeys, addTimestamp);
      JobExecution execution = jobOperator.start(job, jobParameters);

      log.info("Job [{}] 启动成功，执行 ID: {}", job.getName(), execution.getId());
      logJobSummary(job.getName(), execution);
      return execution.getId();

    } catch (Exception e) {
      log.error("Job [{}] 启动失败", job.getName(), e);
      throw new BatchJobExecutionException(job.getName(), e);
    }
  }

  /// 打印 Job 执行摘要：状态、耗时及各 Step 的读/写/过滤/跳过/提交/回滚计数。
  ///
  /// `JobOperator.start()` 在本项目的配置下是同步调用（`TaskExecutorJobLauncher`
  /// 返回时 Job 已执行完毕），因此可以在 launch() 返回前直接读取统计指标。
  ///
  /// 为了兼容单元测试中使用裸 `JobExecution`（无 StepExecution、无时间戳）的场景，
  /// 所有字段访问都做了 null 安全处理。
  ///
  /// @param jobName Job 名称
  /// @param execution 已执行完毕的 JobExecution
  private void logJobSummary(String jobName, JobExecution execution) {
    String status = execution.getStatus() != null ? execution.getStatus().name() : "UNKNOWN";
    String exitCode =
        execution.getExitStatus() != null ? execution.getExitStatus().getExitCode() : "UNKNOWN";
    String duration =
        formatDuration(computeDurationMs(execution.getStartTime(), execution.getEndTime()));

    log.info(
        "Job [{}] 执行完成 [executionId={}, status={}, exitCode={}, duration={}]",
        jobName,
        execution.getId(),
        status,
        exitCode,
        duration);

    var stepExecutions = execution.getStepExecutions();
    if (stepExecutions == null || stepExecutions.isEmpty()) {
      return;
    }
    for (StepExecution step : stepExecutions) {
      String stepDuration =
          formatDuration(computeDurationMs(step.getStartTime(), step.getEndTime()));
      log.info(
          "  └─ Step [{}]: read={}, write={}, filter={}, "
              + "readSkip={}, processSkip={}, writeSkip={}, commit={}, rollback={}, duration={}",
          step.getStepName(),
          step.getReadCount(),
          step.getWriteCount(),
          step.getFilterCount(),
          step.getReadSkipCount(),
          step.getProcessSkipCount(),
          step.getWriteSkipCount(),
          step.getCommitCount(),
          step.getRollbackCount(),
          stepDuration);
    }
  }

  /// 计算两个时间点之间的毫秒数，任一为 null 则返回 0。
  private long computeDurationMs(LocalDateTime start, LocalDateTime end) {
    if (start == null || end == null) {
      return 0L;
    }
    return Duration.between(start, end).toMillis();
  }

  /// 将毫秒数格式化为可读形式：`Xm Ys` 或 `Ys` 或 `Xms`。
  private String formatDuration(long ms) {
    if (ms <= 0L) {
      return "0s";
    }
    if (ms < 1000L) {
      return ms + "ms";
    }
    long totalSeconds = ms / 1000L;
    long minutes = totalSeconds / 60L;
    long seconds = totalSeconds % 60L;
    return minutes > 0 ? String.format("%dm%ds", minutes, seconds) : seconds + "s";
  }

  /// 将 JobParams 转换为 Map。
  ///
  /// 使用 Jackson ObjectMapper 进行转换，null 字段会被自动跳过。
  ///
  /// @param params Job 参数对象
  /// @return 参数 Map
  @SuppressWarnings("unchecked")
  private Map<String, Object> convertToMap(JobParams params) {
    ObjectMapper objectMapper = JsonMapperHolder.getObjectMapper();
    return objectMapper.convertValue(params, Map.class);
  }

  /// 构建 JobParameters。
  ///
  /// 支持常见 Java 类型到 Spring Batch JobParameters 的转换：
  ///
  /// - `String` → `addString`
  /// - `Long` / `Integer` → `addLong`
  /// - `Double` / `Float` → `addDouble`
  /// - `Date` → `addDate`
  /// - 其他类型 → `addString`（通过 `toString()`）
  ///
  /// @param params 参数 Map
  /// @param nonIdentifyingKeys 非标识参数的字段名集合
  /// @param addTimestamp 是否添加时间戳（控制幂等性）
  /// @return JobParameters 实例
  private JobParameters buildJobParameters(
      Map<String, Object> params, Set<String> nonIdentifyingKeys, boolean addTimestamp) {
    JobParametersBuilder builder = new JobParametersBuilder();

    params.forEach(
        (key, value) -> {
          boolean identifying = !nonIdentifyingKeys.contains(key);
          switch (value) {
            case null -> {
              // 跳过 null 值
            }
            case String stringValue -> builder.addString(key, stringValue, identifying);
            case Long longValue -> builder.addLong(key, longValue, identifying);
            case Integer intValue -> builder.addLong(key, intValue.longValue(), identifying);
            case Double doubleValue -> builder.addDouble(key, doubleValue, identifying);
            case Float floatValue -> builder.addDouble(key, floatValue.doubleValue(), identifying);
            case Date dateValue -> builder.addDate(key, dateValue, identifying);
            default -> builder.addString(key, value.toString(), identifying);
          }
        });
    // timestamp 控制幂等性
    // - true: 每次执行都是新 JobInstance
    // - false: 相同参数只执行一次（幂等性）
    if (addTimestamp) {
      builder.addLong("timestamp", System.currentTimeMillis());
    }

    return builder.toJobParameters();
  }

  /// 查询 Job 执行状态。
  ///
  /// **Spring Batch 6.0**：`JobRepository` 现在继承 `JobExplorer` 接口，
  /// 可直接调用 `getJobExecution()` 方法。
  ///
  /// @param executionId JobExecution ID
  /// @return JobExecution（如果存在）
  public Optional<JobExecution> findJobExecution(Long executionId) {
    return Optional.ofNullable(jobRepository.getJobExecution(executionId));
  }
}
