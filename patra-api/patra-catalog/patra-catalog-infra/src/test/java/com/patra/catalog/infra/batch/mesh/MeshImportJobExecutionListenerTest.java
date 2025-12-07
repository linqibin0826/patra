package com.patra.catalog.infra.batch.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;

/// MeshImportJobExecutionListener 单元测试。
///
/// **测试策略**：
///
/// - 验证 Job 启动/完成/失败时的结构化日志输出
/// - 验证不同 Job 状态下的日志内容和格式
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshImportJobExecutionListener 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshImportJobExecutionListenerTest {

  private MeshImportJobExecutionListener listener;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    listener = new MeshImportJobExecutionListener();

    // 设置日志捕获
    logger = (Logger) LoggerFactory.getLogger(MeshImportJobExecutionListener.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
  }

  /// 获取所有 INFO 级别的日志消息。
  private List<String> getInfoLogs() {
    return logAppender.list.stream()
        .filter(event -> event.getLevel() == Level.INFO)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  /// 获取所有 ERROR 级别的日志消息。
  private List<String> getErrorLogs() {
    return logAppender.list.stream()
        .filter(event -> event.getLevel() == Level.ERROR)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  @Nested
  @DisplayName("beforeJob() 日志测试")
  class BeforeJobLogTest {

    @Test
    @DisplayName("应该输出 Job 启动信息")
    void shouldLogJobStartInfo() {
      // Given
      JobExecution jobExecution =
          createJobExecutionWithDetails("meshDescriptorImportJob", 12345L, 67890L);

      // When
      listener.beforeJob(jobExecution);

      // Then
      List<String> infoLogs = getInfoLogs();
      assertThat(infoLogs)
          .anyMatch(log -> log.contains("[Job 启动]"))
          .anyMatch(log -> log.contains("meshDescriptorImportJob"))
          .anyMatch(log -> log.contains("67890")); // Execution ID
    }

    @Test
    @DisplayName("应该输出 Job 参数")
    void shouldLogJobParameters() {
      // Given
      JobParameters params =
          new JobParametersBuilder()
              .addString("meshVersion", "2025")
              .addString("tempFile", "true")
              .addString("filePath", "/tmp/mesh-2025.xml")
              .toJobParameters();

      JobExecution jobExecution = createJobExecutionWithParams("meshDescriptorImportJob", params);

      // When
      listener.beforeJob(jobExecution);

      // Then
      List<String> infoLogs = getInfoLogs();
      assertThat(infoLogs)
          .anyMatch(log -> log.contains("meshVersion=2025"))
          .anyMatch(log -> log.contains("tempFile=true"));
    }
  }

  @Nested
  @DisplayName("afterJob() 日志测试")
  class AfterJobLogTest {

    @Test
    @DisplayName("COMPLETED - 应该输出完成汇总信息")
    void completed_shouldLogSummary() {
      // Given
      JobExecution jobExecution =
          createJobExecutionWithStepMetrics(
              "meshDescriptorImportJob", BatchStatus.COMPLETED, 35000, 35000, 70);

      // When
      listener.afterJob(jobExecution);

      // Then
      List<String> infoLogs = getInfoLogs();
      assertThat(infoLogs)
          .anyMatch(log -> log.contains("[Job 完成]"))
          .anyMatch(log -> log.contains("COMPLETED"))
          .anyMatch(log -> log.contains("35,000") || log.contains("35000"));
    }

    @Test
    @DisplayName("FAILED - 应该输出失败详情")
    void failed_shouldLogFailureDetails() {
      // Given
      JobExecution jobExecution =
          createFailedJobExecution(
              "meshDescriptorImportJob",
              "meshDescriptorImportStep",
              new RuntimeException("Deadlock found"));

      // When
      listener.afterJob(jobExecution);

      // Then
      List<String> errorLogs = getErrorLogs();
      assertThat(errorLogs)
          .anyMatch(log -> log.contains("[Job 失败]"))
          .anyMatch(log -> log.contains("meshDescriptorImportStep"))
          .anyMatch(log -> log.contains("Deadlock"));
    }

    @Test
    @DisplayName("应该计算并显示平均速率")
    void shouldCalculateAndDisplayRate() {
      // Given: 28.5 秒处理 35000 条，速率约 1228 条/秒
      JobExecution jobExecution =
          createJobExecutionWithDuration(
              "meshDescriptorImportJob", BatchStatus.COMPLETED, 35000, 28500);

      // When
      listener.afterJob(jobExecution);

      // Then
      List<String> infoLogs = getInfoLogs();
      assertThat(infoLogs).anyMatch(log -> log.contains("条/秒"));
    }
  }

  /// 创建带有详细信息的 JobExecution。
  private JobExecution createJobExecutionWithDetails(
      String jobName, Long instanceId, Long executionId) {
    JobInstance jobInstance = mock(JobInstance.class);
    when(jobInstance.getJobName()).thenReturn(jobName);
    when(jobInstance.getId()).thenReturn(instanceId);

    JobExecution jobExecution = mock(JobExecution.class);
    when(jobExecution.getJobInstance()).thenReturn(jobInstance);
    when(jobExecution.getId()).thenReturn(executionId);
    when(jobExecution.getJobParameters()).thenReturn(new JobParameters());
    when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now());

    return jobExecution;
  }

  /// 创建带有参数的 JobExecution。
  private JobExecution createJobExecutionWithParams(String jobName, JobParameters params) {
    JobInstance jobInstance = mock(JobInstance.class);
    when(jobInstance.getJobName()).thenReturn(jobName);

    JobExecution jobExecution = mock(JobExecution.class);
    when(jobExecution.getJobInstance()).thenReturn(jobInstance);
    when(jobExecution.getId()).thenReturn(1L);
    when(jobExecution.getJobParameters()).thenReturn(params);
    when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now());

    return jobExecution;
  }

  /// 创建带有 Step 统计信息的 JobExecution。
  private JobExecution createJobExecutionWithStepMetrics(
      String jobName, BatchStatus status, long readCount, long writeCount, int commitCount) {
    JobInstance jobInstance = mock(JobInstance.class);
    when(jobInstance.getJobName()).thenReturn(jobName);

    StepExecution stepExecution = mock(StepExecution.class);
    when(stepExecution.getStepName()).thenReturn("meshDescriptorImportStep");
    when(stepExecution.getReadCount()).thenReturn(readCount);
    when(stepExecution.getWriteCount()).thenReturn(writeCount);
    when(stepExecution.getCommitCount()).thenReturn((long) commitCount);
    when(stepExecution.getStatus()).thenReturn(status);

    JobExecution jobExecution = mock(JobExecution.class);
    when(jobExecution.getJobInstance()).thenReturn(jobInstance);
    when(jobExecution.getId()).thenReturn(1L);
    when(jobExecution.getJobParameters()).thenReturn(new JobParameters());
    when(jobExecution.getStatus()).thenReturn(status);
    when(jobExecution.getStepExecutions()).thenReturn(List.of(stepExecution));
    when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(30));
    when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());

    return jobExecution;
  }

  /// 创建带有执行时长的 JobExecution。
  private JobExecution createJobExecutionWithDuration(
      String jobName, BatchStatus status, long writeCount, long durationMillis) {
    JobInstance jobInstance = mock(JobInstance.class);
    when(jobInstance.getJobName()).thenReturn(jobName);

    StepExecution stepExecution = mock(StepExecution.class);
    when(stepExecution.getStepName()).thenReturn("meshDescriptorImportStep");
    when(stepExecution.getReadCount()).thenReturn(writeCount);
    when(stepExecution.getWriteCount()).thenReturn(writeCount);
    when(stepExecution.getStatus()).thenReturn(status);

    LocalDateTime startTime = LocalDateTime.now().minusNanos(durationMillis * 1_000_000);

    JobExecution jobExecution = mock(JobExecution.class);
    when(jobExecution.getJobInstance()).thenReturn(jobInstance);
    when(jobExecution.getId()).thenReturn(1L);
    when(jobExecution.getJobParameters()).thenReturn(new JobParameters());
    when(jobExecution.getStatus()).thenReturn(status);
    when(jobExecution.getStepExecutions()).thenReturn(List.of(stepExecution));
    when(jobExecution.getStartTime()).thenReturn(startTime);
    when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());

    return jobExecution;
  }

  /// 创建失败的 JobExecution。
  private JobExecution createFailedJobExecution(
      String jobName, String failedStepName, Throwable exception) {
    JobInstance jobInstance = mock(JobInstance.class);
    when(jobInstance.getJobName()).thenReturn(jobName);

    StepExecution stepExecution = mock(StepExecution.class);
    when(stepExecution.getStepName()).thenReturn(failedStepName);
    when(stepExecution.getStatus()).thenReturn(BatchStatus.FAILED);
    when(stepExecution.getFailureExceptions()).thenReturn(List.of(exception));
    when(stepExecution.getReadCount()).thenReturn(15000L);
    when(stepExecution.getWriteCount()).thenReturn(14500L);

    JobExecution jobExecution = mock(JobExecution.class);
    when(jobExecution.getJobInstance()).thenReturn(jobInstance);
    when(jobExecution.getId()).thenReturn(1L);
    when(jobExecution.getJobParameters()).thenReturn(new JobParameters());
    when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
    when(jobExecution.getStepExecutions()).thenReturn(List.of(stepExecution));
    when(jobExecution.getAllFailureExceptions()).thenReturn(List.of(exception));
    when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(15));
    when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());

    return jobExecution;
  }
}
