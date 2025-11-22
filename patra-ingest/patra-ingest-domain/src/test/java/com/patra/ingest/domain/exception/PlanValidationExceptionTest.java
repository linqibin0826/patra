package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.ingest.domain.exception.PlanValidationException.Reason;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link PlanValidationException} 的单元测试。
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("PlanValidationException 单元测试")
class PlanValidationExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 message 构造异常(不指定 reason)")
    void shouldConstructWithMessage() {
      // Given
      String message = "计划验证失败";

      // When
      PlanValidationException exception = new PlanValidationException(message);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isNull();
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 message 和 reason 构造异常")
    void shouldConstructWithMessageAndReason() {
      // Given
      String message = "窗口边界无效";
      Reason reason = Reason.WINDOW_INVALID;

      // When
      PlanValidationException exception = new PlanValidationException(message, reason);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isEqualTo(reason);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 message、reason 和 cause 构造异常")
    void shouldConstructWithMessageReasonAndCause() {
      // Given
      String message = "队列背压检测失败";
      Reason reason = Reason.QUEUE_BACKPRESSURE;
      Throwable cause = new RuntimeException("检测异常");

      // When
      PlanValidationException exception = new PlanValidationException(message, reason, cause);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isEqualTo(reason);
      assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("应该使用 message 和 cause 构造异常(不指定 reason)")
    void shouldConstructWithMessageAndCause() {
      // Given
      String message = "验证过程异常";
      Throwable cause = new IllegalArgumentException("参数错误");

      // When
      PlanValidationException exception = new PlanValidationException(message, cause);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isNull();
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
      PlanValidationException exception =
          new PlanValidationException("验证失败", Reason.WINDOW_INVALID, cause);

      // When & Then
      assertThat(exception.getCause()).isEqualTo(cause);
      assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
  }

  @Nested
  @DisplayName("Reason 枚举测试")
  class ReasonEnumTests {

    @Test
    @DisplayName("应该支持所有预定义的原因")
    void shouldSupportAllPredefinedReasons() {
      // Given & When
      Reason[] reasons = Reason.values();

      // Then
      assertThat(reasons)
          .containsExactlyInAnyOrder(
              Reason.WINDOW_MISSING,
              Reason.WINDOW_INVALID,
              Reason.WINDOW_TOO_LARGE,
              Reason.WINDOW_TOO_SMALL,
              Reason.QUEUE_BACKPRESSURE,
              Reason.CAPABILITY_MISMATCH);
    }

    @Test
    @DisplayName("应该为不同原因创建异常")
    void shouldCreateExceptionForDifferentReasons() {
      // Given & When & Then
      assertThat(new PlanValidationException("msg", Reason.WINDOW_MISSING).getReason())
          .isEqualTo(Reason.WINDOW_MISSING);
      assertThat(new PlanValidationException("msg", Reason.WINDOW_INVALID).getReason())
          .isEqualTo(Reason.WINDOW_INVALID);
      assertThat(new PlanValidationException("msg", Reason.WINDOW_TOO_LARGE).getReason())
          .isEqualTo(Reason.WINDOW_TOO_LARGE);
      assertThat(new PlanValidationException("msg", Reason.WINDOW_TOO_SMALL).getReason())
          .isEqualTo(Reason.WINDOW_TOO_SMALL);
      assertThat(new PlanValidationException("msg", Reason.QUEUE_BACKPRESSURE).getReason())
          .isEqualTo(Reason.QUEUE_BACKPRESSURE);
      assertThat(new PlanValidationException("msg", Reason.CAPABILITY_MISMATCH).getReason())
          .isEqualTo(Reason.CAPABILITY_MISMATCH);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 RULE_VIOLATION 错误特征")
    void shouldContainRuleViolationErrorTrait() {
      // Given
      PlanValidationException exception =
          new PlanValidationException("验证失败", Reason.WINDOW_INVALID);

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
      PlanValidationException exception = new PlanValidationException("验证失败");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      PlanValidationException exception = new PlanValidationException("验证失败");

      // When & Then
      assertThat(exception).isInstanceOf(com.patra.common.error.trait.HasErrorTraits.class);
    }
  }
}
