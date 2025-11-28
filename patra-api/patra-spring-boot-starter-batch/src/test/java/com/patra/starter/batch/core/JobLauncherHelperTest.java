package com.patra.starter.batch.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.starter.batch.exception.BatchJobExecutionException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;

/// {@link JobLauncherHelper} 单元测试
///
/// @author Patra Team
/// @since 1.0.0
@ExtendWith(MockitoExtension.class)
class JobLauncherHelperTest {

  @Mock private JobLauncher jobLauncher;

  @Mock private JobExplorer jobExplorer;

  @Mock private Job job;

  private JobLauncherHelper jobLauncherHelper;

  @BeforeEach
  void setUp() {
    jobLauncherHelper = new JobLauncherHelper(jobLauncher, jobExplorer);
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

      JobExecution mockExecution = new JobExecution(123L);
      when(job.getName()).thenReturn("testJob");
      when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      Long executionId = jobLauncherHelper.launch(job, params, false);

      // Then
      assertThat(executionId).isEqualTo(123L);

      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobLauncher).run(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getString("stringParam")).isEqualTo("test-value");
      assertThat(capturedParams.getLong("longParam")).isEqualTo(42L);
      assertThat(capturedParams.getDouble("doubleParam")).isEqualTo(3.14);
    }

    @Test
    void launch_WithAddTimestampTrue_ShouldIncludeTimestamp() throws Exception {
      // Given
      TestJobParams params = TestJobParams.builder().stringParam("test").build();

      JobExecution mockExecution = new JobExecution(456L);
      when(job.getName()).thenReturn("testJob");
      when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      jobLauncherHelper.launch(job, params, true);

      // Then
      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobLauncher).run(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getLong("timestamp")).isNotNull();
      assertThat(capturedParams.getLong("timestamp")).isGreaterThan(0L);
    }

    @Test
    void launch_WithAddTimestampFalse_ShouldNotIncludeTimestamp() throws Exception {
      // Given
      TestJobParams params = TestJobParams.builder().stringParam("test").build();

      JobExecution mockExecution = new JobExecution(789L);
      when(job.getName()).thenReturn("testJob");
      when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      jobLauncherHelper.launch(job, params, false);

      // Then
      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobLauncher).run(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getLong("timestamp")).isNull();
    }

    @Test
    void launch_DefaultMethod_ShouldAddTimestamp() throws Exception {
      // Given
      TestJobParams params = TestJobParams.builder().stringParam("test").build();

      JobExecution mockExecution = new JobExecution(111L);
      when(job.getName()).thenReturn("testJob");
      when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      jobLauncherHelper.launch(job, params);

      // Then
      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobLauncher).run(eq(job), paramsCaptor.capture());

      JobParameters capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.getLong("timestamp")).isNotNull();
    }

    @Test
    void launch_WhenJobLauncherThrows_ShouldThrowBatchJobExecutionException() throws Exception {
      // Given
      TestJobParams params = TestJobParams.builder().stringParam("test").build();
      when(job.getName()).thenReturn("failingJob");
      when(jobLauncher.run(eq(job), any(JobParameters.class)))
          .thenThrow(new JobExecutionAlreadyRunningException("Job 正在运行"));

      // When & Then
      assertThatThrownBy(() -> jobLauncherHelper.launch(job, params, false))
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

      JobExecution mockExecution = new JobExecution(222L);
      when(job.getName()).thenReturn("testJob");
      when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(mockExecution);

      // When
      jobLauncherHelper.launch(job, params, false);

      // Then
      ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobLauncher).run(eq(job), paramsCaptor.capture());

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
      JobExecution expectedExecution = new JobExecution(executionId);
      when(jobExplorer.getJobExecution(executionId)).thenReturn(expectedExecution);

      // When
      var result = jobLauncherHelper.findJobExecution(executionId);

      // Then
      assertThat(result).isPresent().contains(expectedExecution);
    }

    @Test
    void findJobExecution_WhenNotExists_ShouldReturnEmpty() {
      // Given
      Long executionId = 404L;
      when(jobExplorer.getJobExecution(executionId)).thenReturn(null);

      // When
      var result = jobLauncherHelper.findJobExecution(executionId);

      // Then
      assertThat(result).isEmpty();
    }
  }
}
