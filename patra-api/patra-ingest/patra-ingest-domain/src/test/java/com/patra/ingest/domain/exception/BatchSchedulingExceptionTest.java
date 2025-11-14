package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BatchSchedulingException} 的单元测试。
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("BatchSchedulingException 单元测试")
class BatchSchedulingExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 message 构造异常")
    void shouldConstructWithMessage() {
      // Given
      String message = "批量规划失败:数据源配置缺失";

      // When
      BatchSchedulingException exception = new BatchSchedulingException(message);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 message 和 cause 构造异常")
    void shouldConstructWithMessageAndCause() {
      // Given
      String message = "批量规划失败:元数据查询失败";
      Throwable cause = new RuntimeException("网络连接超时");

      // When
      BatchSchedulingException exception = new BatchSchedulingException(message, cause);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isEqualTo(cause);
    }
  }

  @Nested
  @DisplayName("异常链测试")
  class ExceptionChainTests {

    @Test
    @DisplayName("应该正确传播异常链")
    void shouldPropagateExceptionChain() {
      // Given
      RuntimeException rootCause = new RuntimeException("根本原因");
      IllegalStateException cause = new IllegalStateException("中间原因", rootCause);
      BatchSchedulingException exception = new BatchSchedulingException("规划失败", cause);

      // When & Then
      assertThat(exception.getCause()).isEqualTo(cause);
      assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 IngestException")
    void shouldExtendIngestException() {
      // Given
      BatchSchedulingException exception = new BatchSchedulingException("规划失败");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }
  }
}
