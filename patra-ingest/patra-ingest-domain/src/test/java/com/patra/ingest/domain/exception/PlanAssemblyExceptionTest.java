package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.ingest.domain.exception.PlanAssemblyException.Reason;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link PlanAssemblyException} 的单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PlanAssemblyException 单元测试")
class PlanAssemblyExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 message 构造异常(不指定 reason)")
    void shouldConstructWithMessage() {
      // Given
      String message = "计划组装失败";

      // When
      PlanAssemblyException exception = new PlanAssemblyException(message);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isNull();
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 message 和 reason 构造异常")
    void shouldConstructWithMessageAndReason() {
      // Given
      String message = "窗口内无数据";
      Reason reason = Reason.EMPTY_RESULT;

      // When
      PlanAssemblyException exception = new PlanAssemblyException(message, reason);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isEqualTo(reason);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 message、reason 和 cause 构造异常")
    void shouldConstructWithMessageReasonAndCause() {
      // Given
      String message = "切片生成失败";
      Reason reason = Reason.SLICE_GENERATION_FAILED;
      Throwable cause = new RuntimeException("窗口分区错误");

      // When
      PlanAssemblyException exception = new PlanAssemblyException(message, reason, cause);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isEqualTo(reason);
      assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("应该使用 message 和 cause 构造异常(不指定 reason)")
    void shouldConstructWithMessageAndCause() {
      // Given
      String message = "组装过程异常";
      Throwable cause = new IllegalStateException("状态错误");

      // When
      PlanAssemblyException exception = new PlanAssemblyException(message, cause);

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
      PlanAssemblyException exception =
          new PlanAssemblyException("组装失败", Reason.TASK_GENERATION_FAILED, cause);

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
              Reason.EMPTY_RESULT, Reason.SLICE_GENERATION_FAILED, Reason.TASK_GENERATION_FAILED);
    }

    @Test
    @DisplayName("应该为不同原因创建异常")
    void shouldCreateExceptionForDifferentReasons() {
      // Given & When & Then
      assertThat(new PlanAssemblyException("msg", Reason.EMPTY_RESULT).getReason())
          .isEqualTo(Reason.EMPTY_RESULT);
      assertThat(new PlanAssemblyException("msg", Reason.SLICE_GENERATION_FAILED).getReason())
          .isEqualTo(Reason.SLICE_GENERATION_FAILED);
      assertThat(new PlanAssemblyException("msg", Reason.TASK_GENERATION_FAILED).getReason())
          .isEqualTo(Reason.TASK_GENERATION_FAILED);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 RULE_VIOLATION 错误特征")
    void shouldContainRuleViolationErrorTrait() {
      // Given
      PlanAssemblyException exception = new PlanAssemblyException("组装失败", Reason.EMPTY_RESULT);

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
      PlanAssemblyException exception = new PlanAssemblyException("组装失败");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      PlanAssemblyException exception = new PlanAssemblyException("组装失败");

      // When & Then
      assertThat(exception).isInstanceOf(com.patra.common.error.trait.HasErrorTraits.class);
    }
  }
}
