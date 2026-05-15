package dev.linqibin.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.ingest.domain.exception.OutboxPublishException.Reason;
import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link OutboxPublishException} 的单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OutboxPublishException 单元测试")
class OutboxPublishExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 reason 和 message 构造异常")
    void shouldConstructWithReasonAndMessage() {
      // Given
      Reason reason = Reason.CHANNEL_NOT_ALLOWED;
      String message = "通道不允许发布";

      // When
      OutboxPublishException exception = new OutboxPublishException(reason, message);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isEqualTo(reason);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 reason、message 和 cause 构造异常")
    void shouldConstructWithReasonMessageAndCause() {
      // Given
      Reason reason = Reason.SEND_FAILED;
      String message = "发送到 RocketMQ 失败";
      Throwable cause = new RuntimeException("网络超时");

      // When
      OutboxPublishException exception = new OutboxPublishException(reason, message, cause);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getReason()).isEqualTo(reason);
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
      OutboxPublishException exception =
          new OutboxPublishException(Reason.SEND_FAILED, "发布失败", cause);

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
              Reason.CHANNEL_NOT_ALLOWED, Reason.HEADERS_INVALID, Reason.SEND_FAILED);
    }

    @Test
    @DisplayName("应该正确标识致命错误")
    void shouldCorrectlyIdentifyFatalErrors() {
      // Given & When & Then
      assertThat(Reason.CHANNEL_NOT_ALLOWED.isFatal()).isTrue();
      assertThat(Reason.HEADERS_INVALID.isFatal()).isTrue();
      assertThat(Reason.SEND_FAILED.isFatal()).isFalse();
    }

    @Test
    @DisplayName("应该为不同原因创建异常")
    void shouldCreateExceptionForDifferentReasons() {
      // Given & When & Then
      assertThat(new OutboxPublishException(Reason.CHANNEL_NOT_ALLOWED, "msg").getReason())
          .isEqualTo(Reason.CHANNEL_NOT_ALLOWED);
      assertThat(new OutboxPublishException(Reason.HEADERS_INVALID, "msg").getReason())
          .isEqualTo(Reason.HEADERS_INVALID);
      assertThat(new OutboxPublishException(Reason.SEND_FAILED, "msg").getReason())
          .isEqualTo(Reason.SEND_FAILED);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("致命错误应该包含 RULE_VIOLATION 错误特征")
    void fatalErrorsShouldContainRuleViolationTrait() {
      // Given
      OutboxPublishException channelException =
          new OutboxPublishException(Reason.CHANNEL_NOT_ALLOWED, "通道不允许");
      OutboxPublishException headersException =
          new OutboxPublishException(Reason.HEADERS_INVALID, "消息头无效");

      // When
      Set<ErrorTrait> channelTraits = channelException.getErrorTraits();
      Set<ErrorTrait> headersTraits = headersException.getErrorTraits();

      // Then
      assertThat(channelTraits).containsExactly(StandardErrorTrait.RULE_VIOLATION);
      assertThat(headersTraits).containsExactly(StandardErrorTrait.RULE_VIOLATION);
    }

    @Test
    @DisplayName("可重试错误应该包含 DEP_UNAVAILABLE 错误特征")
    void retryableErrorsShouldContainDepUnavailableTrait() {
      // Given
      OutboxPublishException exception = new OutboxPublishException(Reason.SEND_FAILED, "发送失败");

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 OutboxRelayExecutionException")
    void shouldExtendOutboxRelayExecutionException() {
      // Given
      OutboxPublishException exception = new OutboxPublishException(Reason.SEND_FAILED, "发布失败");

      // When & Then
      assertThat(exception).isInstanceOf(OutboxRelayExecutionException.class);
    }

    @Test
    @DisplayName("应该继承自 IngestException")
    void shouldExtendIngestException() {
      // Given
      OutboxPublishException exception = new OutboxPublishException(Reason.SEND_FAILED, "发布失败");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }
  }
}
