package com.patra.starter.batch.listener;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/// 日志记录 Job 监听器。
///
/// 记录 Spring Batch Job 的启动、完成、失败等关键事件。
///
/// ## 日志级别
///
/// - 启动：INFO
/// - 成功完成：INFO
/// - 失败：ERROR
///
/// @author Patra Team
/// @since 1.0.0
@Slf4j
public class LoggingJobListener implements JobExecutionListener {

  /// Job 启动前触发。
  ///
  /// @param jobExecution Job 执行上下文
  @Override
  public void beforeJob(JobExecution jobExecution) {
    String jobName = jobExecution.getJobInstance().getJobName();
    log.info("Job [{}] 启动，执行 ID: {}", jobName, jobExecution.getId());
  }

  /// Job 完成后触发。
  ///
  /// @param jobExecution Job 执行上下文
  @Override
  public void afterJob(JobExecution jobExecution) {
    String jobName = jobExecution.getJobInstance().getJobName();
    BatchStatus status = jobExecution.getStatus();

    if (status == BatchStatus.COMPLETED) {
      long duration = calculateDuration(jobExecution);
      log.info("Job [{}] 完成，状态: {}, 耗时: {} ms", jobName, status, duration);
    } else if (status == BatchStatus.FAILED) {
      log.error("Job [{}] 失败，状态: {}", jobName, status);

      // 记录异常信息
      jobExecution
          .getAllFailureExceptions()
          .forEach(e -> log.error("Job [{}] 异常详情", jobName, e));
    } else {
      log.warn("Job [{}] 结束，状态: {}", jobName, status);
    }
  }

  /// 计算 Job 执行耗时。
  ///
  /// @param jobExecution Job 执行上下文
  /// @return 耗时（毫秒）
  private long calculateDuration(JobExecution jobExecution) {
    if (jobExecution.getStartTime() == null || jobExecution.getEndTime() == null) {
      return 0;
    }
    return Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
  }
}
