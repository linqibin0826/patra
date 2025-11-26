package com.patra.catalog.infra.batch.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

/// MeshImportJobExecutionListener 单元测试。
///
/// **测试策略**：
///
/// - 验证不同 Job 状态下的临时文件清理行为
/// - COMPLETED/STOPPED：删除临时文件
/// - FAILED：保留临时文件（支持断点续传）
/// - tempFile=false：不删除文件
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshImportJobExecutionListener 单元测试")
class MeshImportJobExecutionListenerTest {

  @TempDir Path tempDir;

  private MeshImportJobExecutionListener listener;

  @BeforeEach
  void setUp() {
    listener = new MeshImportJobExecutionListener();
  }

  @Nested
  @DisplayName("afterJob() 方法测试")
  class AfterJobTest {

    @Test
    @DisplayName("COMPLETED + tempFile=true - 应该删除临时文件")
    void completed_withTempFile_shouldDeleteFile() throws Exception {
      // Given
      Path tempFile = Files.createFile(tempDir.resolve("mesh-import-test.xml"));
      assertThat(Files.exists(tempFile)).isTrue();

      JobExecution jobExecution = createJobExecution(tempFile.toString(), true, BatchStatus.COMPLETED);

      // When
      listener.afterJob(jobExecution);

      // Then
      assertThat(Files.exists(tempFile)).isFalse();
    }

    @Test
    @DisplayName("STOPPED + tempFile=true - 应该删除临时文件")
    void stopped_withTempFile_shouldDeleteFile() throws Exception {
      // Given
      Path tempFile = Files.createFile(tempDir.resolve("mesh-import-stopped.xml"));
      assertThat(Files.exists(tempFile)).isTrue();

      JobExecution jobExecution = createJobExecution(tempFile.toString(), true, BatchStatus.STOPPED);

      // When
      listener.afterJob(jobExecution);

      // Then
      assertThat(Files.exists(tempFile)).isFalse();
    }

    @Test
    @DisplayName("FAILED + tempFile=true - 应该保留临时文件")
    void failed_withTempFile_shouldKeepFile() throws Exception {
      // Given
      Path tempFile = Files.createFile(tempDir.resolve("mesh-import-failed.xml"));
      assertThat(Files.exists(tempFile)).isTrue();

      JobExecution jobExecution = createJobExecution(tempFile.toString(), true, BatchStatus.FAILED);

      // When
      listener.afterJob(jobExecution);

      // Then
      assertThat(Files.exists(tempFile)).isTrue();
    }

    @Test
    @DisplayName("COMPLETED + tempFile=false - 不应该删除文件")
    void completed_withoutTempFile_shouldNotDeleteFile() throws Exception {
      // Given
      Path regularFile = Files.createFile(tempDir.resolve("regular-file.xml"));
      assertThat(Files.exists(regularFile)).isTrue();

      JobExecution jobExecution = createJobExecution(regularFile.toString(), false, BatchStatus.COMPLETED);

      // When
      listener.afterJob(jobExecution);

      // Then
      assertThat(Files.exists(regularFile)).isTrue();
    }

    @Test
    @DisplayName("tempFile 参数缺失 - 不应该删除文件")
    void missingTempFileParam_shouldNotDeleteFile() throws Exception {
      // Given
      Path regularFile = Files.createFile(tempDir.resolve("no-param-file.xml"));
      assertThat(Files.exists(regularFile)).isTrue();

      JobParameters params =
          new JobParametersBuilder().addString("filePath", regularFile.toString()).toJobParameters();
      JobExecution jobExecution = mock(JobExecution.class);
      when(jobExecution.getJobParameters()).thenReturn(params);
      when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

      // When
      listener.afterJob(jobExecution);

      // Then
      assertThat(Files.exists(regularFile)).isTrue();
    }

    @Test
    @DisplayName("filePath 参数缺失 - 应该安全返回")
    void missingFilePath_shouldReturnSafely() {
      // Given
      JobParameters params =
          new JobParametersBuilder().addString("tempFile", "true").toJobParameters();
      JobExecution jobExecution = mock(JobExecution.class);
      when(jobExecution.getJobParameters()).thenReturn(params);
      when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

      // When & Then - 不应该抛出异常
      listener.afterJob(jobExecution);
    }
  }

  private JobExecution createJobExecution(String filePath, boolean tempFile, BatchStatus status) {
    JobParameters params =
        new JobParametersBuilder()
            .addString("filePath", filePath)
            .addString("tempFile", String.valueOf(tempFile))
            .toJobParameters();

    JobExecution jobExecution = mock(JobExecution.class);
    when(jobExecution.getJobParameters()).thenReturn(params);
    when(jobExecution.getStatus()).thenReturn(status);
    return jobExecution;
  }
}
