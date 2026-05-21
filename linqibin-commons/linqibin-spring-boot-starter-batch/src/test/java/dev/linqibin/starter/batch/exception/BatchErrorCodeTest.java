package dev.linqibin.starter.batch.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link BatchErrorCode} 单元测试
 *
 * @author Patra Team
 * @since 1.0.0
 */
class BatchErrorCodeTest {

  @Test
  void jobExecutionFailed_ShouldHaveCorrectCodeAndHttpStatus() {
    // Given
    BatchErrorCode errorCode = BatchErrorCode.JOB_EXECUTION_FAILED;

    // Then
    assertThat(errorCode.code()).isEqualTo("BATCH-0500");
    assertThat(errorCode.httpStatus()).isEqualTo(500);
  }

  @Test
  void jobAlreadyRunning_ShouldHaveCorrectCodeAndHttpStatus() {
    // Given
    BatchErrorCode errorCode = BatchErrorCode.JOB_ALREADY_RUNNING;

    // Then
    assertThat(errorCode.code()).isEqualTo("BATCH-0409");
    assertThat(errorCode.httpStatus()).isEqualTo(409);
  }

  @Test
  void jobNotFound_ShouldHaveCorrectCodeAndHttpStatus() {
    // Given
    BatchErrorCode errorCode = BatchErrorCode.JOB_NOT_FOUND;

    // Then
    assertThat(errorCode.code()).isEqualTo("BATCH-0404");
    assertThat(errorCode.httpStatus()).isEqualTo(404);
  }

  @Test
  void allErrorCodes_ShouldImplementErrorCodeLike() {
    // Given
    BatchErrorCode[] errorCodes = BatchErrorCode.values();

    // Then
    for (BatchErrorCode errorCode : errorCodes) {
      assertThat(errorCode.code()).isNotNull().isNotEmpty();
      assertThat(errorCode.httpStatus()).isBetween(100, 599);
    }
  }
}
