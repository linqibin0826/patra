package com.patra.starter.batch.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.ApplicationException;
import org.junit.jupiter.api.Test;

/**
 * {@link BatchJobExecutionException} 单元测试
 *
 * @author Patra Team
 * @since 1.0.0
 */
class BatchJobExecutionExceptionTest {

  @Test
  void constructor_WithJobNameAndCause_ShouldCreateExceptionWithFormattedMessage() {
    // Given
    String jobName = "meshImportJob";
    Throwable cause = new RuntimeException("根本原因");

    // When
    BatchJobExecutionException exception = new BatchJobExecutionException(jobName, cause);

    // Then
    assertThat(exception).isInstanceOf(ApplicationException.class);
    assertThat(exception.getMessage()).contains("批处理任务执行失败").contains(jobName);
    assertThat(exception.getCause()).isEqualTo(cause);
    assertThat(exception.getErrorCode()).isEqualTo(BatchErrorCode.JOB_EXECUTION_FAILED);
  }

  @Test
  void exception_ShouldExtendApplicationException() {
    // Given
    BatchJobExecutionException exception =
        new BatchJobExecutionException("test job", new RuntimeException("test"));

    // Then
    assertThat(exception).isInstanceOf(ApplicationException.class);
  }

  @Test
  void exception_ShouldHaveCorrectErrorCode() {
    // Given
    BatchJobExecutionException exception =
        new BatchJobExecutionException("test", new RuntimeException());

    // Then
    assertThat(exception.getErrorCode()).isEqualTo(BatchErrorCode.JOB_EXECUTION_FAILED);
    assertThat(exception.getErrorCode().code()).isEqualTo("BATCH_001");
    assertThat(exception.getErrorCode().httpStatus()).isEqualTo(500);
  }
}
