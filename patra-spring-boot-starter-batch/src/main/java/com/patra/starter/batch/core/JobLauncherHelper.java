package com.patra.starter.batch.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import com.patra.starter.batch.exception.BatchJobExecutionException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

/// Job 启动辅助类。
///
/// 封装 Spring Batch JobLauncher 调用逻辑，简化批处理任务启动。
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
/// jobLauncherHelper.launch(meshImportJob, params);
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
public class JobLauncherHelper {

  private final JobLauncher jobLauncher;
  private final JobExplorer jobExplorer;

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
      JobParameters jobParameters = buildJobParameters(paramsMap, addTimestamp);
      JobExecution execution = jobLauncher.run(job, jobParameters);

      log.info("Job [{}] 启动成功，执行 ID: {}", job.getName(), execution.getId());
      return execution.getId();

    } catch (Exception e) {
      log.error("Job [{}] 启动失败", job.getName(), e);
      throw new BatchJobExecutionException(job.getName(), e);
    }
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
  /// @param addTimestamp 是否添加时间戳（控制幂等性）
  /// @return JobParameters 实例
  private JobParameters buildJobParameters(Map<String, Object> params, boolean addTimestamp) {
    JobParametersBuilder builder = new JobParametersBuilder();

    params.forEach(
        (key, value) -> {
          switch (value) {
            case null -> {
              // 跳过 null 值
            }
            case String stringValue -> builder.addString(key, stringValue);
            case Long longValue -> builder.addLong(key, longValue);
            case Integer intValue -> builder.addLong(key, intValue.longValue());
            case Double doubleValue -> builder.addDouble(key, doubleValue);
            case Float floatValue -> builder.addDouble(key, floatValue.doubleValue());
            case Date dateValue -> builder.addDate(key, dateValue);
            default -> builder.addString(key, value.toString());
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
  /// @param executionId JobExecution ID
  /// @return JobExecution（如果存在）
  public Optional<JobExecution> findJobExecution(Long executionId) {
    return Optional.ofNullable(jobExplorer.getJobExecution(executionId));
  }
}
