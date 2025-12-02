package com.patra.starter.batch.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/// Spring Batch 进度指标监听器。
///
/// 补充 Spring Batch 内置指标缺少的数量统计（Counter），
/// 在 Step 执行结束时记录累计的读取/写入/跳过/提交/回滚数量。
///
/// **与内置指标的互补关系**：
///
/// - 内置指标（`spring.batch.*`）：Timer 类型，记录执行耗时
/// - 补充指标（`batch.step.*`）：Counter 类型，记录数量统计
///
/// **指标列表**：
///
/// | 指标名称 | 说明 |
/// |---------|------|
/// | `batch.step.items.read` | 累计读取数量 |
/// | `batch.step.items.written` | 累计写入数量 |
/// | `batch.step.items.skipped` | 累计跳过数量 |
/// | `batch.step.commits` | 累计提交次数 |
/// | `batch.step.rollbacks` | 累计回滚次数 |
///
/// **标签**：
///
/// - `job.name`：Job 名称
/// - `step.name`：Step 名称
///
/// **使用方式**：
///
/// 通过 `BatchProgressMetricsAutoConfiguration` 自动注册为全局 Listener。
///
/// @author linqibin
/// @since 0.1.0
/// @see BatchProgressMetricNames
@Slf4j
public class BatchProgressMetricsListener implements StepExecutionListener {

  private final MeterRegistry meterRegistry;

  /// 构造函数。
  ///
  /// @param meterRegistry Micrometer 指标注册表
  public BatchProgressMetricsListener(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    recordStepMetrics(stepExecution);
    return stepExecution.getExitStatus();
  }

  /// 记录 Step 执行完成后的指标。
  ///
  /// @param stepExecution Step 执行上下文
  private void recordStepMetrics(StepExecution stepExecution) {
    String jobName = stepExecution.getJobExecution().getJobInstance().getJobName();
    String stepName = stepExecution.getStepName();

    try {
      // 记录读取数量
      incrementCounter(
          BatchProgressMetricNames.ITEMS_READ, jobName, stepName, stepExecution.getReadCount());

      // 记录写入数量
      incrementCounter(
          BatchProgressMetricNames.ITEMS_WRITTEN, jobName, stepName, stepExecution.getWriteCount());

      // 记录跳过数量
      incrementCounter(
          BatchProgressMetricNames.ITEMS_SKIPPED, jobName, stepName, stepExecution.getSkipCount());

      // 记录提交次数
      incrementCounter(
          BatchProgressMetricNames.COMMITS, jobName, stepName, stepExecution.getCommitCount());

      // 记录回滚次数
      incrementCounter(
          BatchProgressMetricNames.ROLLBACKS, jobName, stepName, stepExecution.getRollbackCount());

      log.debug(
          "记录 Step 指标完成: job={}, step={}, read={}, written={}, skipped={}, commits={}, rollbacks={}",
          jobName,
          stepName,
          stepExecution.getReadCount(),
          stepExecution.getWriteCount(),
          stepExecution.getSkipCount(),
          stepExecution.getCommitCount(),
          stepExecution.getRollbackCount());

    } catch (Exception e) {
      log.warn("记录 Step 指标失败: job={}, step={}, error={}", jobName, stepName, e.getMessage());
    }
  }

  /// 递增 Counter 指标。
  ///
  /// @param metricName 指标名称
  /// @param jobName Job 名称
  /// @param stepName Step 名称
  /// @param amount 递增量
  private void incrementCounter(String metricName, String jobName, String stepName, long amount) {
    if (amount > 0) {
      Counter.builder(metricName)
          .tag(BatchProgressMetricNames.TAG_JOB_NAME, jobName)
          .tag(BatchProgressMetricNames.TAG_STEP_NAME, stepName)
          .register(meterRegistry)
          .increment(amount);
    }
  }
}
