package com.patra.ingest.domain.event;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OutboxMessageFailedEvent 单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 不使用 Mockito - 使用真实对象
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///
/// 测试范围：
///
/// - ✅ Record 语义测试（equals/hashCode/toString）
///   - ✅ 字段访问器测试
///   - ✅ 领域事件特性测试（occurredAt）
///   - ✅ 不可变性测试
///   - ✅ 边界情况测试
///   - ✅ 业务场景测试（死信消息处理）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OutboxMessageFailedEvent 单元测试")
class OutboxMessageFailedEventTest {

  // ========== Record 构造器测试 ==========

  @Nested
  @DisplayName("Record 构造器")
  class RecordConstructorTests {

    @Test
    @DisplayName("应该成功创建消息永久失败事件")
    void shouldCreateMessageFailedEvent() {
      // Given
      Long messageId = 1001L;
      String channel = "publication.parsed";
      int retryCount = 5;
      String errorCode = "PAYLOAD_INCOMPATIBLE";
      String errorMessage =
          "Message payload format is permanently incompatible with schema version";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.retryCount()).isEqualTo(retryCount);
      assertThat(event.errorCode()).isEqualTo(errorCode);
      assertThat(event.errorMessage()).isEqualTo(errorMessage);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("应该允许 messageId 为 null（异常情况）")
    void shouldAllowNullMessageId() {
      // Given
      Long messageId = null;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              messageId,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.messageId()).isNull();
    }

    @Test
    @DisplayName("应该允许 channel 为 null（异常情况）")
    void shouldAllowNullChannel() {
      // Given
      String channel = null;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, channel, 5, "PAYLOAD_INCOMPATIBLE", "Error message", Instant.now());

      // Then
      assertThat(event.channel()).isNull();
    }

    @Test
    @DisplayName("应该允许 retryCount 为零")
    void shouldAllowZeroRetryCount() {
      // Given
      int retryCount = 0;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              retryCount,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.retryCount()).isZero();
    }

    @Test
    @DisplayName("应该允许 retryCount 为负数（异常情况）")
    void shouldAllowNegativeRetryCount() {
      // Given
      int retryCount = -1;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              retryCount,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.retryCount()).isEqualTo(retryCount);
    }

    @Test
    @DisplayName("应该允许 errorCode 为 null（异常情况）")
    void shouldAllowNullErrorCode() {
      // Given
      String errorCode = null;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, errorCode, "Error message", Instant.now());

      // Then
      assertThat(event.errorCode()).isNull();
    }

    @Test
    @DisplayName("应该允许 errorMessage 为 null（异常情况）")
    void shouldAllowNullErrorMessage() {
      // Given
      String errorMessage = null;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", errorMessage, Instant.now());

      // Then
      assertThat(event.errorMessage()).isNull();
    }

    @Test
    @DisplayName("应该允许 occurredAt 为 null（异常情况）")
    void shouldAllowNullOccurredAt() {
      // Given
      Instant occurredAt = null;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", occurredAt);

      // Then
      assertThat(event.occurredAt()).isNull();
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals - 相同字段值的事件应该相等")
    void shouldImplementEquals_SameFieldValues() {
      // Given
      Long messageId = 1001L;
      String channel = "publication.parsed";
      int retryCount = 5;
      String errorCode = "PAYLOAD_INCOMPATIBLE";
      String errorMessage = "Permanent error";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event1).isEqualTo(event2);
      assertThat(event1.equals(event2)).isTrue();
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 messageId 的事件应该不相等")
    void shouldImplementEquals_DifferentMessageId() {
      // Given
      Instant now = Instant.now();
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", now);
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1002L, // 不同的 messageId
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 channel 的事件应该不相等")
    void shouldImplementEquals_DifferentChannel() {
      // Given
      Instant now = Instant.now();
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", now);
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1001L, "publication.ready", 5, "PAYLOAD_INCOMPATIBLE", "Error message", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 retryCount 的事件应该不相等")
    void shouldImplementEquals_DifferentRetryCount() {
      // Given
      Instant now = Instant.now();
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", now);
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 10, "PAYLOAD_INCOMPATIBLE", "Error message", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 errorCode 的事件应该不相等")
    void shouldImplementEquals_DifferentErrorCode() {
      // Given
      Instant now = Instant.now();
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", now);
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "NETWORK_TIMEOUT", "Error message", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 errorMessage 的事件应该不相等")
    void shouldImplementEquals_DifferentErrorMessage() {
      // Given
      Instant now = Instant.now();
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message 1", now);
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message 2", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 occurredAt 的事件应该不相等")
    void shouldImplementEquals_DifferentOccurredAt() {
      // Given
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.parse("2024-01-15T10:30:00Z"));
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.parse("2024-01-15T10:30:01Z"));

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 相同字段值的事件应该有相同的 hashCode")
    void shouldImplementHashCode_SameFieldValues() {
      // Given
      Long messageId = 1001L;
      String channel = "publication.parsed";
      int retryCount = 5;
      String errorCode = "PAYLOAD_INCOMPATIBLE";
      String errorMessage = "Permanent error";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 不同字段值的事件可能有不同的 hashCode")
    void shouldImplementHashCode_DifferentFieldValues() {
      // Given
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error 1", Instant.now());
      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1002L, "publication.ready", 10, "NETWORK_TIMEOUT", "Error 2", Instant.now());

      // Then - 注意：不同的对象可能有相同的 hashCode（冲突），但通常应该不同
      assertThat(event1.hashCode()).isNotEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString - 包含所有字段信息")
    void shouldImplementToString_ContainsAllFields() {
      // Given
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Permanent error",
              Instant.parse("2024-01-15T10:30:00Z"));

      // When
      String toString = event.toString();

      // Then
      assertThat(toString).contains("OutboxMessageFailedEvent");
      assertThat(toString).contains("messageId=1001");
      assertThat(toString).contains("channel=publication.parsed");
      assertThat(toString).contains("retryCount=5");
      assertThat(toString).contains("errorCode=PAYLOAD_INCOMPATIBLE");
      assertThat(toString).contains("errorMessage=Permanent error");
      assertThat(toString).contains("occurredAt=2024-01-15T10:30:00Z");
    }

    @Test
    @DisplayName("应该正确实现 toString - 处理 null 字段")
    void shouldImplementToString_HandlesNullFields() {
      // Given
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(null, null, 0, null, null, null);

      // When
      String toString = event.toString();

      // Then
      assertThat(toString).contains("OutboxMessageFailedEvent");
      assertThat(toString).contains("messageId=null");
      assertThat(toString).contains("channel=null");
      assertThat(toString).contains("retryCount=0");
      assertThat(toString).contains("errorCode=null");
      assertThat(toString).contains("errorMessage=null");
      assertThat(toString).contains("occurredAt=null");
    }
  }

  // ========== 字段访问器测试 ==========

  @Nested
  @DisplayName("字段访问器测试")
  class FieldAccessorTests {

    @Test
    @DisplayName("应该正确返回 messageId")
    void shouldReturnMessageId() {
      // Given
      Long messageId = 1001L;
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              messageId,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event.messageId()).isEqualTo(messageId);
    }

    @Test
    @DisplayName("应该正确返回 channel")
    void shouldReturnChannel() {
      // Given
      String channel = "publication.parsed";
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, channel, 5, "PAYLOAD_INCOMPATIBLE", "Error message", Instant.now());

      // When & Then
      assertThat(event.channel()).isEqualTo(channel);
    }

    @Test
    @DisplayName("应该正确返回 retryCount")
    void shouldReturnRetryCount() {
      // Given
      int retryCount = 5;
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              retryCount,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event.retryCount()).isEqualTo(retryCount);
    }

    @Test
    @DisplayName("应该正确返回 errorCode")
    void shouldReturnErrorCode() {
      // Given
      String errorCode = "PAYLOAD_INCOMPATIBLE";
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, errorCode, "Error message", Instant.now());

      // When & Then
      assertThat(event.errorCode()).isEqualTo(errorCode);
    }

    @Test
    @DisplayName("应该正确返回 errorMessage")
    void shouldReturnErrorMessage() {
      // Given
      String errorMessage = "Permanent error message";
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", errorMessage, Instant.now());

      // When & Then
      assertThat(event.errorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("应该正确返回 occurredAt")
    void shouldReturnOccurredAt() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", occurredAt);

      // When & Then
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }
  }

  // ========== 领域事件特性测试 ==========

  @Nested
  @DisplayName("领域事件特性测试")
  class DomainEventTests {

    @Test
    @DisplayName("应该实现 OutboxRelayDomainEvent 接口")
    void shouldImplementOutboxRelayDomainEventInterface() {
      // Given
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event).isInstanceOf(OutboxRelayDomainEvent.class);
    }

    @Test
    @DisplayName("应该实现 DomainEvent 接口")
    void shouldImplementDomainEventInterface() {
      // Given
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event).isInstanceOf(dev.linqibin.commons.domain.DomainEvent.class);
    }

    @Test
    @DisplayName("应该正确实现 occurredAt 方法（DomainEvent 接口要求）")
    void shouldImplementOccurredAtMethod() {
      // Given
      Instant expectedOccurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              expectedOccurredAt);

      // When
      Instant actualOccurredAt = event.occurredAt();

      // Then
      assertThat(actualOccurredAt).isEqualTo(expectedOccurredAt);
    }

    @Test
    @DisplayName("应该实现 Serializable 接口（DomainEvent 要求）")
    void shouldImplementSerializableInterface() {
      // Given
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event).isInstanceOf(java.io.Serializable.class);
    }
  }

  // ========== 不可变性测试 ==========

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("应该确保事件为不可变（所有字段为 final）")
    void shouldEnsureEventIsImmutable() {
      // Given
      Long messageId = 1001L;
      String channel = "publication.parsed";
      int retryCount = 5;
      String errorCode = "PAYLOAD_INCOMPATIBLE";
      String errorMessage = "Permanent error";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);

      // When - 保存初始值
      Long initialMessageId = event.messageId();
      String initialChannel = event.channel();
      int initialRetryCount = event.retryCount();
      String initialErrorCode = event.errorCode();
      String initialErrorMessage = event.errorMessage();
      Instant initialOccurredAt = event.occurredAt();

      // Then - 字段应该保持不变（Record 自动为 final，无 setter 方法）
      assertThat(event.messageId()).isEqualTo(initialMessageId);
      assertThat(event.channel()).isEqualTo(initialChannel);
      assertThat(event.retryCount()).isEqualTo(initialRetryCount);
      assertThat(event.errorCode()).isEqualTo(initialErrorCode);
      assertThat(event.errorMessage()).isEqualTo(initialErrorMessage);
      assertThat(event.occurredAt()).isEqualTo(initialOccurredAt);
    }

    @Test
    @DisplayName("应该确保 Instant 字段不可变")
    void shouldEnsureInstantFieldIsImmutable() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", occurredAt);

      // When - Instant 是不可变的，任何修改都会返回新实例
      Instant modifiedInstant = event.occurredAt().plusSeconds(3600);

      // Then - 原事件的 occurredAt 应该保持不变
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
      assertThat(event.occurredAt()).isNotEqualTo(modifiedInstant);
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极大的 messageId")
    void shouldHandleVeryLargeMessageId() {
      // Given
      Long largeMessageId = Long.MAX_VALUE;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              largeMessageId,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.messageId()).isEqualTo(largeMessageId);
    }

    @Test
    @DisplayName("应该处理极小的 messageId（负数）")
    void shouldHandleNegativeMessageId() {
      // Given
      Long negativeMessageId = -1L;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              negativeMessageId,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.messageId()).isEqualTo(negativeMessageId);
    }

    @Test
    @DisplayName("应该处理极大的 retryCount")
    void shouldHandleVeryLargeRetryCount() {
      // Given
      int largeRetryCount = Integer.MAX_VALUE;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              largeRetryCount,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.retryCount()).isEqualTo(largeRetryCount);
    }

    @Test
    @DisplayName("应该处理极小的 retryCount（负数）")
    void shouldHandleNegativeRetryCount() {
      // Given
      int negativeRetryCount = Integer.MIN_VALUE;

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              negativeRetryCount,
              "PAYLOAD_INCOMPATIBLE",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.retryCount()).isEqualTo(negativeRetryCount);
    }

    @Test
    @DisplayName("应该处理极长的 channel 名称")
    void shouldHandleVeryLongChannelName() {
      // Given
      String longChannel = "a".repeat(1000);

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, longChannel, 5, "PAYLOAD_INCOMPATIBLE", "Error message", Instant.now());

      // Then
      assertThat(event.channel()).hasSize(1000);
      assertThat(event.channel()).isEqualTo(longChannel);
    }

    @Test
    @DisplayName("应该处理极长的 errorCode")
    void shouldHandleVeryLongErrorCode() {
      // Given
      String longErrorCode = "ERROR_CODE_".concat("X".repeat(500));

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, longErrorCode, "Error message", Instant.now());

      // Then
      assertThat(event.errorCode()).contains("ERROR_CODE_");
      assertThat(event.errorCode()).hasSize(511);
    }

    @Test
    @DisplayName("应该处理极长的 errorMessage")
    void shouldHandleVeryLongErrorMessage() {
      // Given
      String longErrorMessage =
          "This is a very long error message that exceeds normal limits. ".repeat(100);

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              longErrorMessage,
              Instant.now());

      // Then
      assertThat(event.errorMessage()).contains("This is a very long error message");
      assertThat(event.errorMessage()).hasSizeGreaterThan(1000);
    }

    @Test
    @DisplayName("应该处理极端时间边界（Unix Epoch）")
    void shouldHandleEpochTime() {
      // Given
      Instant epoch = Instant.parse("1970-01-01T00:00:00Z");

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", epoch);

      // Then
      assertThat(event.occurredAt()).isEqualTo(epoch);
    }

    @Test
    @DisplayName("应该处理远期时间")
    void shouldHandleFutureTime() {
      // Given
      Instant future = Instant.parse("2099-12-31T23:59:59Z");

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", future);

      // Then
      assertThat(event.occurredAt()).isEqualTo(future);
    }

    @Test
    @DisplayName("应该处理纳秒级时间精度")
    void shouldHandleNanosecondTimePrecision() {
      // Given
      Instant preciseTime = Instant.parse("2024-01-15T10:30:45.123456789Z");

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error message", preciseTime);

      // Then
      assertThat(event.occurredAt()).isEqualTo(preciseTime);
      assertThat(event.occurredAt().getNano()).isEqualTo(123456789);
    }

    @Test
    @DisplayName("应该处理空字符串字段")
    void shouldHandleEmptyStringFields() {
      // Given
      String emptyChannel = "";
      String emptyErrorCode = "";
      String emptyErrorMessage = "";

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L, emptyChannel, 5, emptyErrorCode, emptyErrorMessage, Instant.now());

      // Then
      assertThat(event.channel()).isEmpty();
      assertThat(event.errorCode()).isEmpty();
      assertThat(event.errorMessage()).isEmpty();
    }

    @Test
    @DisplayName("应该处理包含特殊字符的字段")
    void shouldHandleSpecialCharactersInFields() {
      // Given
      String channelWithSpecialChars = "publication.parsed:v1.0-beta@2024";
      String errorCodeWithSpecialChars = "PAYLOAD_ERROR_#123!@$%";
      String errorMessageWithSpecialChars =
          "Failed to parse JSON: unexpected token '}' at line 42, column 15";

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              channelWithSpecialChars,
              5,
              errorCodeWithSpecialChars,
              errorMessageWithSpecialChars,
              Instant.now());

      // Then
      assertThat(event.channel()).isEqualTo(channelWithSpecialChars);
      assertThat(event.errorCode()).isEqualTo(errorCodeWithSpecialChars);
      assertThat(event.errorMessage()).isEqualTo(errorMessageWithSpecialChars);
    }

    @Test
    @DisplayName("应该处理包含换行符的 errorMessage")
    void shouldHandleErrorMessageWithNewlines() {
      // Given
      String multilineErrorMessage = "Error occurred:\nLine 1: Syntax error\nLine 2: Invalid token";

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              multilineErrorMessage,
              Instant.now());

      // Then
      assertThat(event.errorMessage()).contains("\n");
      assertThat(event.errorMessage()).contains("Line 1");
      assertThat(event.errorMessage()).contains("Line 2");
    }

    @Test
    @DisplayName("应该处理包含 Unicode 字符的 errorMessage")
    void shouldHandleUnicodeCharactersInErrorMessage() {
      // Given
      String unicodeErrorMessage = "解析失败：出版物数据格式不兼容 💀";

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              "PAYLOAD_INCOMPATIBLE",
              unicodeErrorMessage,
              Instant.now());

      // Then
      assertThat(event.errorMessage()).isEqualTo(unicodeErrorMessage);
      assertThat(event.errorMessage()).contains("解析失败");
      assertThat(event.errorMessage()).contains("💀");
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenarioTests {

    @Test
    @DisplayName("应该正确记录达到重试限制的死信场景")
    void shouldRecordDeadLetterScenarioAfterRetryLimit() {
      // Given - 消息重试 5 次后仍失败
      Long messageId = 1001L;
      String channel = "publication.parsed";
      int maxRetries = 5;
      String errorCode = "NETWORK_TIMEOUT";
      String errorMessage = "Failed to publish message after 5 retries: network timeout";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              messageId, channel, maxRetries, errorCode, errorMessage, occurredAt);

      // Then - 应该完整记录死信信息
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.retryCount()).isEqualTo(maxRetries);
      assertThat(event.errorCode()).isEqualTo(errorCode);
      assertThat(event.errorMessage()).contains("after 5 retries");
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("应该正确记录不可恢复错误的死信场景")
    void shouldRecordIrrecoverableErrorScenario() {
      // Given - 负载格式永久不兼容，不需要重试
      Long messageId = 2001L;
      String channel = "publication.ready";
      int retryCount = 0; // 直接失败，不重试
      String errorCode = "PAYLOAD_INCOMPATIBLE";
      String errorMessage =
          "Message payload format is permanently incompatible with schema version 2.0";
      Instant occurredAt = Instant.parse("2024-01-15T11:00:00Z");

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);

      // Then - 应该记录永久性错误
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.retryCount()).isZero(); // 不需要重试
      assertThat(event.errorCode()).isEqualTo("PAYLOAD_INCOMPATIBLE");
      assertThat(event.errorMessage()).contains("permanently incompatible");
    }

    @Test
    @DisplayName("应该支持按错误代码聚合分析场景")
    void shouldSupportErrorCodeAggregationScenario() {
      // Given - 多个消息因相同错误失败
      String commonErrorCode = "PAYLOAD_INCOMPATIBLE";
      Instant baseTime = Instant.parse("2024-01-15T10:00:00Z");

      // When - 创建 3 个失败事件，错误代码相同
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L,
              "publication.parsed",
              5,
              commonErrorCode,
              "Schema validation failed for message 1001",
              baseTime);

      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1002L,
              "publication.parsed",
              5,
              commonErrorCode,
              "Schema validation failed for message 1002",
              baseTime.plusSeconds(10));

      OutboxMessageFailedEvent event3 =
          new OutboxMessageFailedEvent(
              1003L,
              "publication.parsed",
              5,
              commonErrorCode,
              "Schema validation failed for message 1003",
              baseTime.plusSeconds(20));

      // Then - 应该可以按错误代码聚合，快速定位热点失败模式
      assertThat(event1.errorCode()).isEqualTo(commonErrorCode);
      assertThat(event2.errorCode()).isEqualTo(commonErrorCode);
      assertThat(event3.errorCode()).isEqualTo(commonErrorCode);
      assertThat(event1.errorCode()).isEqualTo(event2.errorCode()).isEqualTo(event3.errorCode());
    }

    @Test
    @DisplayName("应该支持按通道分析失败热点场景")
    void shouldSupportChannelBasedFailureHotspotAnalysisScenario() {
      // Given - 分析不同通道的失败率
      String highFailureChannel = "publication.parsed";
      String lowFailureChannel = "provenance.changed";

      // When
      OutboxMessageFailedEvent highFailureEvent1 =
          new OutboxMessageFailedEvent(
              1001L, highFailureChannel, 5, "PAYLOAD_INCOMPATIBLE", "Error 1", Instant.now());

      OutboxMessageFailedEvent highFailureEvent2 =
          new OutboxMessageFailedEvent(
              1002L, highFailureChannel, 5, "NETWORK_TIMEOUT", "Error 2", Instant.now());

      OutboxMessageFailedEvent lowFailureEvent =
          new OutboxMessageFailedEvent(
              2001L, lowFailureChannel, 5, "NETWORK_TIMEOUT", "Error 3", Instant.now());

      // Then - 应该可以识别高失败率通道
      assertThat(highFailureEvent1.channel()).isEqualTo(highFailureChannel);
      assertThat(highFailureEvent2.channel()).isEqualTo(highFailureChannel);
      assertThat(lowFailureEvent.channel()).isEqualTo(lowFailureChannel);
      assertThat(highFailureEvent1.channel()).isEqualTo(highFailureEvent2.channel());
    }

    @Test
    @DisplayName("应该支持告警驱动场景")
    void shouldSupportAlertingScenario() {
      // Given - 短时间内多次失败应该触发告警
      String channel = "publication.parsed";
      String criticalErrorCode = "PAYLOAD_INCOMPATIBLE";
      Instant alertTime = Instant.parse("2024-01-15T10:00:00Z");

      // When - 1 分钟内 3 次相同错误
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L, channel, 5, criticalErrorCode, "Error in message 1001", alertTime);

      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1002L,
              channel,
              5,
              criticalErrorCode,
              "Error in message 1002",
              alertTime.plusSeconds(30));

      OutboxMessageFailedEvent event3 =
          new OutboxMessageFailedEvent(
              1003L,
              channel,
              5,
              criticalErrorCode,
              "Error in message 1003",
              alertTime.plusSeconds(60));

      // Then - 应该可以检测到告警条件
      assertThat(event1.errorCode()).isEqualTo(criticalErrorCode);
      assertThat(event2.errorCode()).isEqualTo(criticalErrorCode);
      assertThat(event3.errorCode()).isEqualTo(criticalErrorCode);

      // 时间窗口检查
      assertThat(event3.occurredAt().getEpochSecond() - event1.occurredAt().getEpochSecond())
          .isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("应该支持补偿重放场景")
    void shouldSupportCompensationReplayScenario() {
      // Given - 记录死信消息，供手动或离线重放工具使用
      Long deadLetterMessageId = 9999L;
      String channel = "publication.parsed";
      int retryCount = 5;
      String errorCode = "DOWNSTREAM_SERVICE_UNAVAILABLE";
      String errorMessage = "Downstream service unavailable after 5 retries, marked as dead letter";
      Instant failedAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxMessageFailedEvent deadLetterEvent =
          new OutboxMessageFailedEvent(
              deadLetterMessageId, channel, retryCount, errorCode, errorMessage, failedAt);

      // Then - 应该完整记录用于补偿的关键信息
      assertThat(deadLetterEvent.messageId()).isEqualTo(deadLetterMessageId);
      assertThat(deadLetterEvent.channel()).isEqualTo(channel);
      assertThat(deadLetterEvent.retryCount()).isEqualTo(retryCount);
      assertThat(deadLetterEvent.errorCode()).isEqualTo(errorCode);
      assertThat(deadLetterEvent.errorMessage()).contains("dead letter");
      assertThat(deadLetterEvent.occurredAt()).isEqualTo(failedAt);
    }

    @Test
    @DisplayName("应该支持不同错误类型的分类场景")
    void shouldSupportErrorTypeClassificationScenario() {
      // Given - 区分临时性错误和永久性错误
      Instant now = Instant.now();

      // 临时性错误 - 可能因网络抖动引起
      OutboxMessageFailedEvent transientError =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "NETWORK_TIMEOUT", "Temporary network timeout", now);

      // 永久性错误 - 负载格式问题
      OutboxMessageFailedEvent permanentError =
          new OutboxMessageFailedEvent(
              1002L,
              "publication.parsed",
              0,
              "PAYLOAD_INCOMPATIBLE",
              "Permanent schema incompatibility",
              now);

      // Then - 应该可以通过错误代码区分错误类型
      assertThat(transientError.errorCode()).isEqualTo("NETWORK_TIMEOUT");
      assertThat(transientError.retryCount()).isEqualTo(5); // 重试过

      assertThat(permanentError.errorCode()).isEqualTo("PAYLOAD_INCOMPATIBLE");
      assertThat(permanentError.retryCount()).isZero(); // 不需要重试
    }

    @Test
    @DisplayName("应该支持时间序列分析场景")
    void shouldSupportTimeSeriesAnalysisScenario() {
      // Given - 按时间序列记录失败事件
      Instant t1 = Instant.parse("2024-01-15T10:00:00Z");
      Instant t2 = Instant.parse("2024-01-15T11:00:00Z");
      Instant t3 = Instant.parse("2024-01-15T12:00:00Z");

      // When
      OutboxMessageFailedEvent event1 =
          new OutboxMessageFailedEvent(
              1001L, "publication.parsed", 5, "NETWORK_TIMEOUT", "Error at 10:00", t1);

      OutboxMessageFailedEvent event2 =
          new OutboxMessageFailedEvent(
              1002L, "publication.parsed", 5, "PAYLOAD_INCOMPATIBLE", "Error at 11:00", t2);

      OutboxMessageFailedEvent event3 =
          new OutboxMessageFailedEvent(
              1003L, "publication.parsed", 5, "NETWORK_TIMEOUT", "Error at 12:00", t3);

      // Then - 应该可以按时间排序分析失败趋势
      assertThat(event1.occurredAt()).isBefore(event2.occurredAt());
      assertThat(event2.occurredAt()).isBefore(event3.occurredAt());
      assertThat(event1.occurredAt()).isEqualTo(t1);
      assertThat(event2.occurredAt()).isEqualTo(t2);
      assertThat(event3.occurredAt()).isEqualTo(t3);
    }

    @Test
    @DisplayName("应该支持零重试场景（快速失败）")
    void shouldSupportZeroRetryFastFailScenario() {
      // Given - 某些错误应该立即失败，不重试
      Long messageId = 3001L;
      String channel = "publication.parsed";
      int retryCount = 0; // 快速失败
      String errorCode = "INVALID_MESSAGE_FORMAT";
      String errorMessage = "Message format is invalid, retry will not help";
      Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event.retryCount()).isZero();
      assertThat(event.errorCode()).isEqualTo("INVALID_MESSAGE_FORMAT");
      assertThat(event.errorMessage()).contains("retry will not help");
    }

    @Test
    @DisplayName("应该支持高重试次数场景（顽固失败）")
    void shouldSupportHighRetryCountScenario() {
      // Given - 某些消息经过多次重试仍失败
      Long messageId = 4001L;
      String channel = "publication.parsed";
      int retryCount = 100; // 非常高的重试次数
      String errorCode = "INTERMITTENT_FAILURE";
      String errorMessage = "Message failed after 100 retries due to intermittent errors";
      Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");

      // When
      OutboxMessageFailedEvent event =
          new OutboxMessageFailedEvent(
              messageId, channel, retryCount, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event.retryCount()).isEqualTo(100);
      assertThat(event.errorMessage()).contains("100 retries");
    }
  }
}
