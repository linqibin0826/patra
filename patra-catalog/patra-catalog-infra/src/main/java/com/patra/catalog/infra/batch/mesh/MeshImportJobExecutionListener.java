package com.patra.catalog.infra.batch.mesh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

/// MeSH 导入 Job 执行监听器。
///
/// **职责**：
///
/// 1. 输出结构化的 Job 启动/完成/失败日志
/// 2. 在 Job 完成后清理临时文件
///
/// **日志格式**：
///
/// - 启动时：显示 Job 名称、Execution ID、启动时间、参数
/// - 完成时：显示状态、执行时间、读取/写入数量、速率
/// - 失败时：显示失败 Step 和原因
///
/// **清理策略**：
///
/// - COMPLETED：删除临时文件（导入成功，文件不再需要）
/// - STOPPED：删除临时文件（用户手动停止，文件不再需要）
/// - FAILED：保留临时文件（支持断点续传，下次启动可继续）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class MeshImportJobExecutionListener implements JobExecutionListener {

  private static final String SEPARATOR = "=".repeat(50);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.CHINA);

  @Override
  public void beforeJob(JobExecution jobExecution) {
    logJobStart(jobExecution);
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    logJobEnd(jobExecution);
    cleanupTempFileIfNeeded(jobExecution);
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
        NUMBER_FORMAT.format(totalReadCount),
        NUMBER_FORMAT.format(totalWriteCount),
        NUMBER_FORMAT.format(totalCommitCount),
        NUMBER_FORMAT.format((long) rate),
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
        {}""",
        SEPARATOR,
        jobName,
        formatDuration(duration),
        NUMBER_FORMAT.format(readCount),
        failedStepName,
        failureReason,
        SEPARATOR);
  }

  // ==================== 工具方法 ====================

  /// 格式化 Job 参数（隐藏敏感路径，只显示文件名）。
  private String formatJobParameters(JobParameters params) {
    if (params == null || params.isEmpty()) {
      return "无";
    }

    return params.getParameters().entrySet().stream()
        .map(
            entry -> {
              String key = entry.getKey();
              String value = String.valueOf(entry.getValue().getValue());
              // 对 filePath 只显示文件名
              if ("filePath".equals(key) && value.contains("/")) {
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

  /// 格式化时长（如：28.5 秒）。
  private String formatDuration(Duration duration) {
    long totalMillis = duration.toMillis();
    if (totalMillis < 1000) {
      return totalMillis + " 毫秒";
    }
    double seconds = totalMillis / 1000.0;
    return String.format("%.1f 秒", seconds);
  }

  // ==================== 原有的临时文件清理逻辑 ====================

  /// 清理临时文件（如果需要）。
  private void cleanupTempFileIfNeeded(JobExecution jobExecution) {
    JobParameters params = jobExecution.getJobParameters();
    String filePath = params.getString("filePath");
    String tempFileStr = params.getString("tempFile");

    // 如果 filePath 缺失或 tempFile 不是 true，直接返回
    if (filePath == null || !"true".equalsIgnoreCase(tempFileStr)) {
      return;
    }

    BatchStatus status = jobExecution.getStatus();
    if (status == BatchStatus.COMPLETED || status == BatchStatus.STOPPED) {
      try {
        Path path = Path.of(filePath);
        boolean deleted = Files.deleteIfExists(path);
        if (deleted) {
          log.info("Job 完成，已清理临时文件：{}，状态：{}", filePath, status);
        }
      } catch (IOException e) {
        log.warn("清理临时文件失败：{}，原因：{}", filePath, e.getMessage());
      }
    } else {
      log.info("Job 状态为 {}，保留临时文件以支持续传：{}", status, filePath);
    }
  }
}
