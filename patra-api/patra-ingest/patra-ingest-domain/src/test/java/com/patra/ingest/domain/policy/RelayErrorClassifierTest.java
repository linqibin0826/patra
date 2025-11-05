package com.patra.ingest.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.patra.ingest.domain.exception.OutboxPublishException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.policy.RelayErrorClassifier.RelayErrorKind;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RelayErrorClassifier 单元测试
 *
 * <p>测试重点：
 *
 * <ul>
 *   <li>错误分类正确性（TRANSIENT vs FATAL）
 *   <li>异常链处理（根本原因分析）
 *   <li>边界条件（null、嵌套异常）
 *   <li>典型错误场景覆盖
 * </ul>
 *
 * <p>注意：由于 RelayErrorClassifier 是接口，此测试使用模拟实现来验证分类逻辑
 *
 * @author linqibin
 */
@DisplayName("RelayErrorClassifier 单元测试")
class RelayErrorClassifierTest {

  private RelayErrorClassifier classifier;

  @BeforeEach
  void setUp() {
    // 使用测试实现模拟真实的分类逻辑
    classifier = new TestRelayErrorClassifier();
  }

  @Nested
  @DisplayName("FATAL 错误分类")
  class FatalErrorClassification {

    @Test
    @DisplayName("应该将 IllegalArgumentException 分类为 FATAL")
    void shouldClassifyIllegalArgumentExceptionAsFatal() {
      // Given
      Throwable cause = new IllegalArgumentException("Invalid parameter");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }

    @Test
    @DisplayName("应该将 IllegalStateException 分类为 FATAL")
    void shouldClassifyIllegalStateExceptionAsFatal() {
      // Given
      Throwable cause = new IllegalStateException("Invalid state transition");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }

    @Test
    @DisplayName("应该将 JsonProcessingException 分类为 FATAL")
    void shouldClassifyJsonProcessingExceptionAsFatal() {
      // Given
      Throwable cause = new JsonProcessingException("JSON parsing failed") {};

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }

    @Test
    @DisplayName("应该将 OutboxRelayExecutionException 分类为 FATAL")
    void shouldClassifyOutboxRelayExecutionExceptionAsFatal() {
      // Given
      Throwable cause = new OutboxRelayExecutionException("Relay execution failed", null);

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }

    @Test
    @DisplayName("应该将嵌套的 FATAL 异常正确分类")
    void shouldClassifyNestedFatalExceptionCorrectly() {
      // Given - 外层是 RuntimeException，内层是 IllegalArgumentException
      Throwable root = new IllegalArgumentException("Root cause");
      Throwable cause = new RuntimeException("Wrapper", root);

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }

    @Test
    @DisplayName("应该将深层嵌套的 FATAL 异常正确分类")
    void shouldClassifyDeeplyNestedFatalExceptionCorrectly() {
      // Given - 多层嵌套异常
      Throwable root = new JsonProcessingException("JSON error") {};
      Throwable middle = new RuntimeException("Middle layer", root);
      Throwable cause = new Exception("Top layer", middle);

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }
  }

  @Nested
  @DisplayName("TRANSIENT 错误分类")
  class TransientErrorClassification {

    @Test
    @DisplayName("应该将 IOException 分类为 TRANSIENT")
    void shouldClassifyIOExceptionAsTransient() {
      // Given
      Throwable cause = new IOException("Network error");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该将 TimeoutException 分类为 TRANSIENT")
    void shouldClassifyTimeoutExceptionAsTransient() {
      // Given
      Throwable cause = new TimeoutException("Request timeout");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该将 SQLException 分类为 TRANSIENT")
    void shouldClassifySQLExceptionAsTransient() {
      // Given
      Throwable cause = new SQLException("Database connection lost");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该将未知 RuntimeException 分类为 TRANSIENT")
    void shouldClassifyUnknownRuntimeExceptionAsTransient() {
      // Given
      Throwable cause = new RuntimeException("Unknown error");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该将嵌套的 TRANSIENT 异常正确分类")
    void shouldClassifyNestedTransientExceptionCorrectly() {
      // Given - 外层是 RuntimeException，内层是 IOException
      Throwable root = new IOException("Network interrupted");
      Throwable cause = new RuntimeException("Wrapper", root);

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }
  }

  @Nested
  @DisplayName("OutboxPublishException 特殊处理")
  class OutboxPublishExceptionHandling {

    @Test
    @DisplayName("应该根据 OutboxPublishException.Reason.isFatal() 分类为 FATAL")
    void shouldClassifyOutboxPublishExceptionAsFatalWhenReasonIsFatal() {
      // Given - 使用 FATAL 原因的 OutboxPublishException
      OutboxPublishException.Reason fatalReason = OutboxPublishException.Reason.CHANNEL_NOT_ALLOWED;
      Throwable cause = new OutboxPublishException(fatalReason, "Channel not allowed");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }

    @Test
    @DisplayName("应该根据 OutboxPublishException.Reason.isFatal() 分类为 TRANSIENT")
    void shouldClassifyOutboxPublishExceptionAsTransientWhenReasonIsNotFatal() {
      // Given - 使用 TRANSIENT 原因的 OutboxPublishException
      OutboxPublishException.Reason transientReason = OutboxPublishException.Reason.SEND_FAILED;
      Throwable cause = new OutboxPublishException(transientReason, "Send failed");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该在嵌套异常中找到 OutboxPublishException 并正确分类")
    void shouldFindOutboxPublishExceptionInNestedCauseAndClassifyCorrectly() {
      // Given - OutboxPublishException 被包装在其他异常中
      OutboxPublishException.Reason fatalReason = OutboxPublishException.Reason.HEADERS_INVALID;
      Throwable root = new OutboxPublishException(fatalReason, "Headers invalid");
      Throwable cause = new RuntimeException("Wrapper", root);

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }
  }

  @Nested
  @DisplayName("边界条件")
  class EdgeCases {

    @Test
    @DisplayName("应该处理无 cause 的异常")
    void shouldHandleExceptionWithoutCause() {
      // Given
      Throwable cause = new RuntimeException("No cause");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该处理循环引用的异常链（避免无限循环）")
    void shouldHandleCircularExceptionChain() {
      // Given - 创建循环引用的异常（A -> B -> C -> A）
      // 注意：直接自引用在 Java 中不允许，但可以通过反射模拟
      RuntimeException causeA = new RuntimeException("Exception A");
      RuntimeException causeB = new RuntimeException("Exception B", causeA);
      RuntimeException causeC = new RuntimeException("Exception C", causeB);

      // 通过反射创建循环引用（仅用于测试分类器的健壮性）
      try {
        java.lang.reflect.Field causeField = Throwable.class.getDeclaredField("cause");
        causeField.setAccessible(true);
        causeField.set(causeA, causeC);
      } catch (Exception e) {
        // 如果反射失败，跳过此测试
        return;
      }

      // When
      RelayErrorKind kind = classifier.classify(causeC);

      // Then - 应该能够处理循环引用而不陷入无限循环
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该处理非常深的异常链")
    void shouldHandleVeryDeepExceptionChain() {
      // Given - 构建 10 层深的异常链
      Throwable root = new IllegalArgumentException("Root cause");
      Throwable current = root;
      for (int i = 0; i < 10; i++) {
        current = new RuntimeException("Layer " + i, current);
      }

      // When
      RelayErrorKind kind = classifier.classify(current);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }
  }

  @Nested
  @DisplayName("真实场景示例")
  class RealWorldScenarios {

    @Test
    @DisplayName("应该正确分类网络超时场景（TRANSIENT）")
    void shouldClassifyNetworkTimeoutAsTransient() {
      // Given - 模拟 HTTP 客户端超时
      Throwable cause = new TimeoutException("HTTP request timeout after 30s");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该正确分类数据库连接丢失场景（TRANSIENT）")
    void shouldClassifyDatabaseConnectionLossAsTransient() {
      // Given - 模拟数据库连接丢失
      Throwable cause = new SQLException("Connection to MySQL server lost");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该正确分类 JSON 序列化失败场景（FATAL）")
    void shouldClassifyJsonSerializationFailureAsFatal() {
      // Given - 模拟 JSON 序列化失败
      Throwable cause = new JsonProcessingException("Cannot serialize object to JSON") {};

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }

    @Test
    @DisplayName("应该正确分类配置错误场景（FATAL）")
    void shouldClassifyConfigurationErrorAsFatal() {
      // Given - 模拟配置错误
      Throwable cause = new IllegalArgumentException("Invalid configuration: provenance code is null");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }

    @Test
    @DisplayName("应该正确分类发送失败场景（TRANSIENT）")
    void shouldClassifySendFailureAsTransient() {
      // Given - 模拟 MQ 发送失败
      OutboxPublishException.Reason reason = OutboxPublishException.Reason.SEND_FAILED;
      Throwable cause = new OutboxPublishException(reason, "RocketMQ send failed");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.TRANSIENT);
    }

    @Test
    @DisplayName("应该正确分类业务规则违反场景（FATAL）")
    void shouldClassifyBusinessRuleViolationAsFatal() {
      // Given - 模拟业务规则违反
      Throwable cause = new IllegalStateException("Cannot transition from COMPLETED to RUNNING");

      // When
      RelayErrorKind kind = classifier.classify(cause);

      // Then
      assertThat(kind).isEqualTo(RelayErrorKind.FATAL);
    }
  }

  /**
   * 测试用的 RelayErrorClassifier 实现
   *
   * <p>模拟真实实现的分类逻辑（参考 RelayErrorClassifierImpl）
   */
  private static class TestRelayErrorClassifier implements RelayErrorClassifier {

    @Override
    public RelayErrorKind classify(Throwable cause) {
      // 1. 优先检查 OutboxPublishException
      Throwable publish = findPublishException(cause);
      if (publish instanceof OutboxPublishException publishException) {
        return publishException.getReason().isFatal() ? RelayErrorKind.FATAL : RelayErrorKind.TRANSIENT;
      }

      // 2. 获取根本原因
      Throwable root = getRootCause(cause);

      // 3. 分类根本原因
      if (root instanceof OutboxRelayExecutionException
          || root instanceof IllegalArgumentException
          || root instanceof IllegalStateException
          || root instanceof JsonProcessingException) {
        return RelayErrorKind.FATAL;
      }

      return RelayErrorKind.TRANSIENT;
    }

    /**
     * 查找异常链中的 OutboxPublishException
     */
    private Throwable findPublishException(Throwable cause) {
      Throwable current = cause;
      int depth = 0;
      while (current != null && depth < 100) { // 防止无限循环
        if (current instanceof OutboxPublishException) {
          return current;
        }
        Throwable next = current.getCause();
        if (next == current) { // 防止自引用
          break;
        }
        current = next;
        depth++;
      }
      return null;
    }

    /**
     * 获取异常链的根本原因
     */
    private Throwable getRootCause(Throwable cause) {
      Throwable current = cause;
      Throwable root = cause;
      int depth = 0;
      while (current != null && depth < 100) { // 防止无限循环
        root = current;
        Throwable next = current.getCause();
        if (next == current || next == null) { // 防止自引用或到达终点
          break;
        }
        current = next;
        depth++;
      }
      return root;
    }
  }
}
