package dev.linqibin.starter.batch.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.step.StepExecution;

/// BatchProgressMetricsListener 单元测试。
///
/// 验证进度指标监听器的 Counter 指标记录行为：
///
/// - afterStep 时应该记录正确的 Counter 值
/// - 标签应该包含 job.name 和 step.name
/// - 不同 Job/Step 组合应该使用不同的 Counter 实例
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("BatchProgressMetricsListener 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class BatchProgressMetricsListenerTest {

  private MeterRegistry meterRegistry;
  private BatchProgressMetricsListener listener;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    listener = new BatchProgressMetricsListener(meterRegistry);
  }

  @Nested
  @DisplayName("afterStep() 指标记录测试")
  class AfterStepMetricsTest {

    @Test
    @DisplayName("应该记录 items.read Counter")
    void shouldRecordItemsReadCounter() {
      // Given
      StepExecution stepExecution =
          createStepExecution("testJob", "testStep", 1000, 950, 50, 10, 2);

      // When
      listener.afterStep(stepExecution);

      // Then
      double count =
          meterRegistry
              .counter(
                  BatchProgressMetricNames.ITEMS_READ,
                  BatchProgressMetricNames.TAG_JOB_NAME,
                  "testJob",
                  BatchProgressMetricNames.TAG_STEP_NAME,
                  "testStep")
              .count();
      assertThat(count).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("应该记录 items.written Counter")
    void shouldRecordItemsWrittenCounter() {
      // Given
      StepExecution stepExecution =
          createStepExecution("testJob", "testStep", 1000, 950, 50, 10, 2);

      // When
      listener.afterStep(stepExecution);

      // Then
      double count =
          meterRegistry
              .counter(
                  BatchProgressMetricNames.ITEMS_WRITTEN,
                  BatchProgressMetricNames.TAG_JOB_NAME,
                  "testJob",
                  BatchProgressMetricNames.TAG_STEP_NAME,
                  "testStep")
              .count();
      assertThat(count).isEqualTo(950.0);
    }

    @Test
    @DisplayName("应该记录 items.skipped Counter")
    void shouldRecordItemsSkippedCounter() {
      // Given
      StepExecution stepExecution =
          createStepExecution("testJob", "testStep", 1000, 950, 50, 10, 2);

      // When
      listener.afterStep(stepExecution);

      // Then
      double count =
          meterRegistry
              .counter(
                  BatchProgressMetricNames.ITEMS_SKIPPED,
                  BatchProgressMetricNames.TAG_JOB_NAME,
                  "testJob",
                  BatchProgressMetricNames.TAG_STEP_NAME,
                  "testStep")
              .count();
      assertThat(count).isEqualTo(50.0);
    }

    @Test
    @DisplayName("应该记录 commits Counter")
    void shouldRecordCommitsCounter() {
      // Given
      StepExecution stepExecution =
          createStepExecution("testJob", "testStep", 1000, 950, 50, 10, 2);

      // When
      listener.afterStep(stepExecution);

      // Then
      double count =
          meterRegistry
              .counter(
                  BatchProgressMetricNames.COMMITS,
                  BatchProgressMetricNames.TAG_JOB_NAME,
                  "testJob",
                  BatchProgressMetricNames.TAG_STEP_NAME,
                  "testStep")
              .count();
      assertThat(count).isEqualTo(10.0);
    }

    @Test
    @DisplayName("应该记录 rollbacks Counter")
    void shouldRecordRollbacksCounter() {
      // Given
      StepExecution stepExecution =
          createStepExecution("testJob", "testStep", 1000, 950, 50, 10, 2);

      // When
      listener.afterStep(stepExecution);

      // Then
      double count =
          meterRegistry
              .counter(
                  BatchProgressMetricNames.ROLLBACKS,
                  BatchProgressMetricNames.TAG_JOB_NAME,
                  "testJob",
                  BatchProgressMetricNames.TAG_STEP_NAME,
                  "testStep")
              .count();
      assertThat(count).isEqualTo(2.0);
    }

    @Test
    @DisplayName("不同 Step 应该使用不同的 Counter 实例")
    void differentSteps_shouldUseDifferentCounters() {
      // Given
      StepExecution step1 = createStepExecution("job1", "step1", 100, 100, 0, 1, 0);
      StepExecution step2 = createStepExecution("job1", "step2", 200, 200, 0, 2, 0);

      // When
      listener.afterStep(step1);
      listener.afterStep(step2);

      // Then
      double step1Count =
          meterRegistry
              .counter(
                  BatchProgressMetricNames.ITEMS_READ,
                  BatchProgressMetricNames.TAG_JOB_NAME,
                  "job1",
                  BatchProgressMetricNames.TAG_STEP_NAME,
                  "step1")
              .count();
      double step2Count =
          meterRegistry
              .counter(
                  BatchProgressMetricNames.ITEMS_READ,
                  BatchProgressMetricNames.TAG_JOB_NAME,
                  "job1",
                  BatchProgressMetricNames.TAG_STEP_NAME,
                  "step2")
              .count();

      assertThat(step1Count).isEqualTo(100.0);
      assertThat(step2Count).isEqualTo(200.0);
    }
  }

  /// 创建带有统计数据的 StepExecution。
  ///
  /// @param jobName Job 名称
  /// @param stepName Step 名称
  /// @param readCount 读取数量
  /// @param writeCount 写入数量
  /// @param skipCount 跳过数量
  /// @param commitCount 提交次数
  /// @param rollbackCount 回滚次数
  /// @return StepExecution mock 对象
  private StepExecution createStepExecution(
      String jobName,
      String stepName,
      long readCount,
      long writeCount,
      long skipCount,
      long commitCount,
      long rollbackCount) {
    JobInstance jobInstance = mock(JobInstance.class);
    when(jobInstance.getJobName()).thenReturn(jobName);

    JobExecution jobExecution = mock(JobExecution.class);
    when(jobExecution.getJobInstance()).thenReturn(jobInstance);

    StepExecution stepExecution = mock(StepExecution.class);
    when(stepExecution.getStepName()).thenReturn(stepName);
    when(stepExecution.getJobExecution()).thenReturn(jobExecution);
    when(stepExecution.getReadCount()).thenReturn(readCount);
    when(stepExecution.getWriteCount()).thenReturn(writeCount);
    when(stepExecution.getSkipCount()).thenReturn(skipCount);
    when(stepExecution.getCommitCount()).thenReturn(commitCount);
    when(stepExecution.getRollbackCount()).thenReturn(rollbackCount);
    when(stepExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

    return stepExecution;
  }
}
