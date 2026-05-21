package dev.linqibin.starter.batch.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.json.JsonMapperHolder;
import dev.linqibin.starter.batch.exception.BatchJobExecutionException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import tools.jackson.databind.json.JsonMapper;

/// {@link JobOperatorHelper} 单元测试
///
/// @author Patra Team
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
class JobOperatorHelperTest {

  @Mock private JobOperator jobOperator;

  @Mock private JobRepository jobRepository;

  @Mock private Job job;

  private JobOperatorHelper jobOperatorHelper;

  /// 注册一个不含 LongToStringModule 的干净 ObjectMapper。
  ///
  /// 当 Spring IT 测试（如 BatchAutoConfigurationIT）在同一 JVM 进程中
  /// 先于本测试运行时，会通过 ObjectMapperProvider 将带有 LongToStringModule
  /// 的 Spring ObjectMapper 注册到 JsonMapperHolder。该模块将 Long 序列化为
  /// String，导致 convertValue 后 longParam 类型变为 String，最终
  /// JobParameters.getLong() 抛出 IllegalArgumentException。
  ///
  /// 此方法在测试类加载时重置 JsonMapperHolder 为标准 JsonMapper，
  /// 避免跨测试类的静态状态污染。
  @BeforeAll
  static void resetJsonMapper() {
    JsonMapperHolder.register(JsonMapper.builder().findAndAddModules().build());
  }

  @BeforeEach
  void setUp() {
    jobOperatorHelper = new JobOperatorHelper(jobOperator, jobRepository);
  }

  /// 创建用于测试的 JobExecution 实例。
  ///
  /// Spring Batch 6.0：JobExecution 构造函数需要 JobInstance 和 JobParameters。
  private JobExecution createJobExecution(long id) {
    JobInstance jobInstance = new JobInstance(id, "testJob");
    return new JobExecution(id, jobInstance, new JobParameters());
  }

  /// 测试用 JobParams 实现
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  static class TestJobParams implements JobParams {
    private String stringParam;
    private Long longParam;
    private Double doubleParam;
  }

  @Nested
  class Launch {

    @Test
    void launch_WithJobParams_ShouldConvertToJobParameters() throws Exception {
      // Given
      TestJobParams params =
          TestJobParams.builder()
              .stringParam("test-value")
              .longParam(42L)
              .doubleParam(3.14)
              .build();

      JobExecution mockExecution = createJobExecution(123L);
      when(job.getName()).thenReturn("testJob");
      when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      Long executionId = jobOperatorHelper.launch(job, params, false);

      // Then
      assertThat(executionId).isEqualTo(123L);

      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobOperator).start(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getString("stringParam")).isEqualTo("test-value");
      assertThat(capturedParams.getLong("longParam")).isEqualTo(42L);
      assertThat(capturedParams.getDouble("doubleParam")).isEqualTo(3.14);
    }

    @Test
    void launch_WithAddTimestampTrue_ShouldIncludeTimestamp() throws Exception {
      // Given
      TestJobParams params = TestJobParams.builder().stringParam("test").build();

      JobExecution mockExecution = createJobExecution(456L);
      when(job.getName()).thenReturn("testJob");
      when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      jobOperatorHelper.launch(job, params, true);

      // Then
      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobOperator).start(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getLong("timestamp")).isNotNull();
      assertThat(capturedParams.getLong("timestamp")).isGreaterThan(0L);
    }

    @Test
    void launch_WithAddTimestampFalse_ShouldNotIncludeTimestamp() throws Exception {
      // Given
      TestJobParams params = TestJobParams.builder().stringParam("test").build();

      JobExecution mockExecution = createJobExecution(789L);
      when(job.getName()).thenReturn("testJob");
      when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      jobOperatorHelper.launch(job, params, false);

      // Then
      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobOperator).start(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getLong("timestamp")).isNull();
    }

    @Test
    void launch_DefaultMethod_ShouldAddTimestamp() throws Exception {
      // Given
      TestJobParams params = TestJobParams.builder().stringParam("test").build();

      JobExecution mockExecution = createJobExecution(111L);
      when(job.getName()).thenReturn("testJob");
      when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      jobOperatorHelper.launch(job, params);

      // Then
      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobOperator).start(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getLong("timestamp")).isNotNull();
    }

    @Test
    void launch_WhenJobOperatorThrows_ShouldThrowBatchJobExecutionException() throws Exception {
      // Given
      TestJobParams params = TestJobParams.builder().stringParam("test").build();
      when(job.getName()).thenReturn("failingJob");
      when(jobOperator.start(eq(job), any(JobParameters.class)))
          .thenThrow(new JobExecutionAlreadyRunningException("Job 正在运行"));

      // When & Then
      assertThatThrownBy(() -> jobOperatorHelper.launch(job, params, false))
          .isInstanceOf(BatchJobExecutionException.class)
          .hasMessageContaining("failingJob");
    }

    @Test
    void launch_WithNullField_ShouldSkipNullValues() throws Exception {
      // Given
      TestJobParams params =
          TestJobParams.builder()
              .stringParam("only-string")
              .longParam(null) // null 值应该被跳过
              .doubleParam(null)
              .build();

      JobExecution mockExecution = createJobExecution(222L);
      when(job.getName()).thenReturn("testJob");
      when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      jobOperatorHelper.launch(job, params, false);

      // Then
      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobOperator).start(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getString("stringParam")).isEqualTo("only-string");
      assertThat(capturedParams.getLong("longParam")).isNull();
      assertThat(capturedParams.getDouble("doubleParam")).isNull();
    }
  }

  @Nested
  class FindJobExecution {

    @Test
    void findJobExecution_WhenExists_ShouldReturnExecution() {
      // Given
      Long executionId = 999L;
      JobExecution expectedExecution = createJobExecution(executionId);
      when(jobRepository.getJobExecution(executionId)).thenReturn(expectedExecution);

      // When
      var result = jobOperatorHelper.findJobExecution(executionId);

      // Then
      assertThat(result).isPresent().contains(expectedExecution);
    }

    @Test
    void findJobExecution_WhenNotExists_ShouldReturnEmpty() {
      // Given
      Long executionId = 404L;
      when(jobRepository.getJobExecution(executionId)).thenReturn(null);

      // When
      var result = jobOperatorHelper.findJobExecution(executionId);

      // Then
      assertThat(result).isEmpty();
    }
  }
}
