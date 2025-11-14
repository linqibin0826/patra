package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.trait.ErrorTrait;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link IngestScheduleParameterException} 的单元测试。
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("IngestScheduleParameterException 单元测试")
class IngestScheduleParameterExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 message 构造异常")
    void shouldConstructWithMessage() {
      // Given
      String message = "缺少必填参数: provenanceCode";

      // When
      IngestScheduleParameterException exception = new IngestScheduleParameterException(message);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 message 和 cause 构造异常")
    void shouldConstructWithMessageAndCause() {
      // Given
      String message = "JSON 参数解析失败";
      Throwable cause = new RuntimeException("格式错误");

      // When
      IngestScheduleParameterException exception =
          new IngestScheduleParameterException(message, cause);

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
      IllegalArgumentException cause = new IllegalArgumentException("参数错误", rootCause);
      IngestScheduleParameterException exception =
          new IngestScheduleParameterException("调度参数异常", cause);

      // When & Then
      assertThat(exception.getCause()).isEqualTo(cause);
      assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 RULE_VIOLATION 错误特征")
    void shouldContainRuleViolationErrorTrait() {
      // Given
      IngestScheduleParameterException exception = new IngestScheduleParameterException("参数错误");

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(ErrorTrait.RULE_VIOLATION);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 IngestException")
    void shouldExtendIngestException() {
      // Given
      IngestScheduleParameterException exception = new IngestScheduleParameterException("参数错误");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      IngestScheduleParameterException exception = new IngestScheduleParameterException("参数错误");

      // When & Then
      assertThat(exception).isInstanceOf(com.patra.common.error.trait.HasErrorTraits.class);
    }
  }
}
