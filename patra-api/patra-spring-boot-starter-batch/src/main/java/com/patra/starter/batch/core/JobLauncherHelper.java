package com.patra.starter.batch.core;

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
import org.springframework.stereotype.Component;

/**
 * Job 启动辅助类
 *
 * <p>封装 Spring Batch JobLauncher 调用逻辑，简化批处理任务启动
 *
 * <p><strong>关键改进</strong>：支持可选的 timestamp 参数，控制 Job 幂等性：
 *
 * <ul>
 *   <li>{@code addTimestamp=true}: 每次执行都创建新 JobInstance（适用于可重复执行的任务）
 *   <li>{@code addTimestamp=false}: 相同参数的 Job 只执行一次（幂等性保证）
 * </ul>
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobLauncherHelper {

  private final JobLauncher jobLauncher;
  private final JobExplorer jobExplorer;

  /**
   * 启动 Job（默认添加 timestamp，每次创建新实例）
   *
   * @param job Job 实例
   * @param params Job 参数
   * @return JobExecution ID
   */
  public Long launch(Job job, Map<String, Object> params) {
    return launch(job, params, true);
  }

  /**
   * 启动 Job
   *
   * <p><strong>关键改进</strong>：支持可选的 timestamp 参数
   *
   * @param job Job 实例
   * @param params Job 参数（Map 形式）
   * @param addTimestamp 是否添加时间戳
   *     <ul>
   *       <li>{@code true}: 每次执行都创建新 JobInstance（适用于可重复执行的任务）
   *       <li>{@code false}: 相同参数的 Job 只执行一次（幂等性保证）
   *     </ul>
   *
   * @return JobExecution ID
   * @throws BatchJobExecutionException Job 启动失败时抛出
   */
  public Long launch(Job job, Map<String, Object> params, boolean addTimestamp) {
    try {
      JobParameters jobParameters = buildJobParameters(params, addTimestamp);
      JobExecution execution = jobLauncher.run(job, jobParameters);

      log.info("Job [{}] 启动成功，执行 ID: {}", job.getName(), execution.getId());
      return execution.getId();

    } catch (Exception e) {
      log.error("Job [{}] 启动失败", job.getName(), e);
      throw new BatchJobExecutionException(job.getName(), e);
    }
  }

  /**
   * 构建 JobParameters
   *
   * <p>支持常见 Java 类型到 Spring Batch JobParameters 的转换
   *
   * @param params 参数 Map
   * @param addTimestamp 是否添加时间戳（控制幂等性）
   * @return JobParameters 实例
   */
  private JobParameters buildJobParameters(Map<String, Object> params, boolean addTimestamp) {
    JobParametersBuilder builder = new JobParametersBuilder();

    params.forEach(
        (key, value) -> {
          if (value instanceof String) {
            builder.addString(key, (String) value);
          } else if (value instanceof Long) {
            builder.addLong(key, (Long) value);
          } else if (value instanceof Double) {
            builder.addDouble(key, (Double) value);
          } else if (value instanceof Date) {
            builder.addDate(key, (Date) value);
          } else {
            builder.addString(key, value.toString());
          }
        });

    // ⭐ 关键改进：timestamp 可选
    // - true: 每次执行都是新 JobInstance
    // - false: 相同参数只执行一次（幂等性）
    if (addTimestamp) {
      builder.addLong("timestamp", System.currentTimeMillis());
    }

    return builder.toJobParameters();
  }

  /**
   * 查询 Job 执行状态
   *
   * @param executionId JobExecution ID
   * @return JobExecution（如果存在）
   */
  public Optional<JobExecution> findJobExecution(Long executionId) {
    return Optional.ofNullable(jobExplorer.getJobExecution(executionId));
  }
}
