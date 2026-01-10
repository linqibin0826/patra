package com.patra.catalog.infra.adapter.batch.author;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

/// PubMed Computed Authors 导入 Job 执行监听器。
///
/// **职责**：
///
/// - 输出结构化的 Job 启动/完成/失败日志
///
/// **日志格式**：
///
/// - 启动时：显示 Job 名称、Execution ID、启动时间、参数
/// - 完成时：显示状态、执行时间、读取/写入数量、速率
/// - 失败时：显示失败 Step 和原因
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class AuthorImportJobExecutionListener implements JobExecutionListener {

  private static final String SEPARATOR = "=".repeat(60);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public void beforeJob(JobExecution jobExecution) {
    logJobStart(jobExecution);
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    logJobEnd(jobExecution);
  }

  // ==================== 日志输出方法 ====================

  /// 输出 Job 启动日志。
  private void logJobStart(JobExecution jobExecution) {
    String jobName = jobExecution.getJobInstance().getJobName();
    Long executionId = jobExecution.getId();
    LocalDateTime startTime = jobExecution.getStartTime();
    String paramsStr = formatJobParameters(jobExecution.getJobParameters());

    log.info(
        """
        {}
        [Job 启动] {}
          - Execution ID: {}
          - 启动时间: {}
          - 参数: {}
          - 预计数据量: 2100 万+ 条（约 3.6GB）
        {}""",
        SEPARATOR,
        jobName,
        executionId,
        startTime != null ? startTime.format(TIME_FORMATTER) : "N/A",
        paramsStr,
        SEPARATOR);
  }

  /// 输出 Job 结束日志。
  private void logJobEnd(JobExecution jobExecution) {
    BatchStatus status = jobExecution.getStatus();

    if (status == BatchStatus.FAILED) {
      logJobFailure(jobExecution);
    } else {
      logJobCompletion(jobExecution);
    }
  }

  /// 输出 Job 完成日志。
  private void logJobCompletion(JobExecution jobExecution) {
    String jobName = jobExecution.getJobInstance().getJobName();
    BatchStatus status = jobExecution.getStatus();
    Duration duration = calculateDuration(jobExecution);

    // 汇总所有 Step 的统计数据
    long totalReadCount = 0;
    long totalWriteCount = 0;
    long totalCommitCount = 0;

    for (StepExecution step : jobExecution.getStepExecutions()) {
      totalReadCount += step.getReadCount();
      totalWriteCount += step.getWriteCount();
      totalCommitCount += step.getCommitCount();
    }

    // 计算平均速率
    double rate = duration.toMillis() > 0 ? (totalWriteCount * 1000.0) / duration.toMillis() : 0;

    log.info(
        """
        {}
        [Job 完成] {}
          - 状态: {}
          - 执行时间: {}
          - 读取记录: {} 条
          - 写入记录: {} 条
          - 提交次数: {} 次
          - 平均速率: {} 条/秒
        {}""",
        SEPARATOR,
        jobName,
        status,
        formatDuration(duration),
        formatNumber(totalReadCount),
        formatNumber(totalWriteCount),
        formatNumber(totalCommitCount),
        formatNumber((long) rate),
        SEPARATOR);
  }

  /// 输出 Job 失败日志。
  private void logJobFailure(JobExecution jobExecution) {
    String jobName = jobExecution.getJobInstance().getJobName();
    Duration duration = calculateDuration(jobExecution);

    // 查找失败的 Step
    String failedStepName = "N/A";
    long readCount = 0;
    String failureReason = "未知";

    for (StepExecution step : jobExecution.getStepExecutions()) {
      if (step.getStatus() == BatchStatus.FAILED) {
        failedStepName = step.getStepName();
        readCount = step.getReadCount();
        if (!step.getFailureExceptions().isEmpty()) {
          failureReason = step.getFailureExceptions().get(0).getMessage();
        }
        break;
      }
    }

    log.error(
        """
        {}
        [Job 失败] {}
          - 状态: FAILED
          - 执行时间: {}
          - 读取记录: {} 条
          - 失败 Step: {}
          - 失败原因: {}
          - 提示: 可通过 XXL-Job 重新触发，支持断点续传
        {}""",
        SEPARATOR,
        jobName,
        formatDuration(duration),
        formatNumber(readCount),
        failedStepName,
        failureReason,
        SEPARATOR);
  }

  // ==================== 工具方法 ====================

  /// 格式化数字为千分位格式。
  ///
  /// 使用方法级别创建 NumberFormat 以确保线程安全。
  ///
  /// @param value 数值
  /// @return 格式化后的字符串（如 `1,234`）
  private String formatNumber(long value) {
    return NumberFormat.getNumberInstance(Locale.CHINA).format(value);
  }

  /// 格式化 Job 参数（对 URL 只显示文件名部分）。
  private String formatJobParameters(JobParameters params) {
    if (params == null || params.isEmpty()) {
      return "无";
    }

    return params.getParameters().entrySet().stream()
        .map(
            entry -> {
              String key = entry.getKey();
              String value = String.valueOf(entry.getValue().getValue());
              // 对 downloadUrl 只显示文件名
              if ("downloadUrl".equals(key) && value.contains("/")) {
                value = value.substring(value.lastIndexOf('/') + 1);
              }
              return key + "=" + value;
            })
        .collect(Collectors.joining(", "));
  }

  /// 计算执行时长。
  private Duration calculateDuration(JobExecution jobExecution) {
    LocalDateTime startTime = jobExecution.getStartTime();
    LocalDateTime endTime = jobExecution.getEndTime();
    if (startTime == null || endTime == null) {
      return Duration.ZERO;
    }
    return Duration.between(startTime, endTime);
  }

  /// 格式化时长。
  ///
  /// 短时间显示秒，长时间显示 HH:mm:ss。
  private String formatDuration(Duration duration) {
    long totalSeconds = duration.getSeconds();
    if (totalSeconds < 60) {
      return String.format("%.1f 秒", duration.toMillis() / 1000.0);
    }
    return String.format(
        "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60);
  }
}
