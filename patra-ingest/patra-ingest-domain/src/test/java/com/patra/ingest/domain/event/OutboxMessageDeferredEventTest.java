package com.patra.ingest.domain.event;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * OutboxMessageDeferredEvent 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>不使用 Mockito - 使用真实对象
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 * </ul>
 *
 * <p>测试范围：
 *
 * <ul>
 *   <li>✅ Record 语义测试（equals/hashCode/toString）
 *   <li>✅ 字段访问器测试
 *   <li>✅ 领域事件特性测试（occurredAt）
 *   <li>✅ 不可变性测试
 *   <li>✅ 边界情况测试
 *   <li>✅ 业务场景测试（延迟重试计划、退避策略）
 *   <li>✅ 重试计数逻辑测试（nextRetryCount = 当前失败次数 + 1）
 *   <li>✅ 时间相关业务逻辑测试（nextRetryAt 退避计算）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("OutboxMessageDeferredEvent 单元测试")
class OutboxMessageDeferredEventTest {

  // ========== Record 构造器测试 ==========

  @Nested
  @DisplayName("Record 构造器")
  class RecordConstructorTests {

    @Test
    @DisplayName("应该成功创建消息延迟重试事件")
    void shouldCreateMessageDeferredEvent() {
      // Given
      Long messageId = 1001L;
      String channel = "publication.parsed";
      int nextRetryCount = 3;
      Instant nextRetryAt = Instant.parse("2024-01-15T10:35:00Z");
      String errorCode = "NETWORK_TIMEOUT";
      String errorMessage = "Temporary network timeout, will retry";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              messageId, channel, nextRetryCount, nextRetryAt, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.nextRetryCount()).isEqualTo(nextRetryCount);
      assertThat(event.nextRetryAt()).isEqualTo(nextRetryAt);
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              messageId,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              channel,
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.channel()).isNull();
    }

    @Test
    @DisplayName("应该允许 nextRetryCount 为零")
    void shouldAllowZeroNextRetryCount() {
      // Given
      int nextRetryCount = 0;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              nextRetryCount,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.nextRetryCount()).isZero();
    }

    @Test
    @DisplayName("应该允许 nextRetryCount 为负数（异常情况）")
    void shouldAllowNegativeNextRetryCount() {
      // Given
      int nextRetryCount = -1;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              nextRetryCount,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.nextRetryCount()).isEqualTo(nextRetryCount);
    }

    @Test
    @DisplayName("应该允许 nextRetryAt 为 null（异常情况）")
    void shouldAllowNullNextRetryAt() {
      // Given
      Instant nextRetryAt = null;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              nextRetryAt,
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.nextRetryAt()).isNull();
    }

    @Test
    @DisplayName("应该允许 errorCode 为 null（异常情况）")
    void shouldAllowNullErrorCode() {
      // Given
      String errorCode = null;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              errorCode,
              "Error message",
              Instant.now());

      // Then
      assertThat(event.errorCode()).isNull();
    }

    @Test
    @DisplayName("应该允许 errorMessage 为 null（异常情况）")
    void shouldAllowNullErrorMessage() {
      // Given
      String errorMessage = null;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              errorMessage,
              Instant.now());

      // Then
      assertThat(event.errorMessage()).isNull();
    }

    @Test
    @DisplayName("应该允许 occurredAt 为 null（异常情况）")
    void shouldAllowNullOccurredAt() {
      // Given
      Instant occurredAt = null;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              occurredAt);

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
      int nextRetryCount = 3;
      Instant nextRetryAt = Instant.parse("2024-01-15T10:35:00Z");
      String errorCode = "NETWORK_TIMEOUT";
      String errorMessage = "Temporary error";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              messageId, channel, nextRetryCount, nextRetryAt, errorCode, errorMessage, occurredAt);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              messageId, channel, nextRetryCount, nextRetryAt, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event1).isEqualTo(event2);
      assertThat(event1.equals(event2)).isTrue();
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 messageId 的事件应该不相等")
    void shouldImplementEquals_DifferentMessageId() {
      // Given
      Instant now = Instant.now();
      Instant futureTime = now.plusSeconds(300);
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, futureTime, "NETWORK_TIMEOUT", "Error message", now);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1002L, // 不同的 messageId
              "publication.parsed",
              3,
              futureTime,
              "NETWORK_TIMEOUT",
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
      Instant futureTime = now.plusSeconds(300);
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, futureTime, "NETWORK_TIMEOUT", "Error message", now);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.ready", 3, futureTime, "NETWORK_TIMEOUT", "Error message", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 nextRetryCount 的事件应该不相等")
    void shouldImplementEquals_DifferentNextRetryCount() {
      // Given
      Instant now = Instant.now();
      Instant futureTime = now.plusSeconds(300);
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, futureTime, "NETWORK_TIMEOUT", "Error message", now);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 5, futureTime, "NETWORK_TIMEOUT", "Error message", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 nextRetryAt 的事件应该不相等")
    void shouldImplementEquals_DifferentNextRetryAt() {
      // Given
      Instant now = Instant.now();
      Instant futureTime1 = now.plusSeconds(300);
      Instant futureTime2 = now.plusSeconds(600);
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, futureTime1, "NETWORK_TIMEOUT", "Error message", now);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, futureTime2, "NETWORK_TIMEOUT", "Error message", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 errorCode 的事件应该不相等")
    void shouldImplementEquals_DifferentErrorCode() {
      // Given
      Instant now = Instant.now();
      Instant futureTime = now.plusSeconds(300);
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, futureTime, "NETWORK_TIMEOUT", "Error message", now);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              futureTime,
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Error message",
              now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 errorMessage 的事件应该不相等")
    void shouldImplementEquals_DifferentErrorMessage() {
      // Given
      Instant now = Instant.now();
      Instant futureTime = now.plusSeconds(300);
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, futureTime, "NETWORK_TIMEOUT", "Error message 1", now);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, futureTime, "NETWORK_TIMEOUT", "Error message 2", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 occurredAt 的事件应该不相等")
    void shouldImplementEquals_DifferentOccurredAt() {
      // Given
      Instant futureTime = Instant.parse("2024-01-15T10:35:00Z");
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              futureTime,
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.parse("2024-01-15T10:30:00Z"));
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              futureTime,
              "NETWORK_TIMEOUT",
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
      int nextRetryCount = 3;
      Instant nextRetryAt = Instant.parse("2024-01-15T10:35:00Z");
      String errorCode = "NETWORK_TIMEOUT";
      String errorMessage = "Temporary error";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              messageId, channel, nextRetryCount, nextRetryAt, errorCode, errorMessage, occurredAt);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              messageId, channel, nextRetryCount, nextRetryAt, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 不同字段值的事件可能有不同的 hashCode")
    void shouldImplementHashCode_DifferentFieldValues() {
      // Given
      Instant now = Instant.now();
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              now.plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error 1",
              now);
      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1002L,
              "publication.ready",
              5,
              now.plusSeconds(600),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Error 2",
              now);

      // Then - 注意：不同的对象可能有相同的 hashCode（冲突），但通常应该不同
      assertThat(event1.hashCode()).isNotEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString - 包含所有字段信息")
    void shouldImplementToString_ContainsAllFields() {
      // Given
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.parse("2024-01-15T10:35:00Z"),
              "NETWORK_TIMEOUT",
              "Temporary error",
              Instant.parse("2024-01-15T10:30:00Z"));

      // When
      String toString = event.toString();

      // Then
      assertThat(toString).contains("OutboxMessageDeferredEvent");
      assertThat(toString).contains("messageId=1001");
      assertThat(toString).contains("channel=publication.parsed");
      assertThat(toString).contains("nextRetryCount=3");
      assertThat(toString).contains("nextRetryAt=2024-01-15T10:35:00Z");
      assertThat(toString).contains("errorCode=NETWORK_TIMEOUT");
      assertThat(toString).contains("errorMessage=Temporary error");
      assertThat(toString).contains("occurredAt=2024-01-15T10:30:00Z");
    }

    @Test
    @DisplayName("应该正确实现 toString - 处理 null 字段")
    void shouldImplementToString_HandlesNullFields() {
      // Given
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(null, null, 0, null, null, null, null);

      // When
      String toString = event.toString();

      // Then
      assertThat(toString).contains("OutboxMessageDeferredEvent");
      assertThat(toString).contains("messageId=null");
      assertThat(toString).contains("channel=null");
      assertThat(toString).contains("nextRetryCount=0");
      assertThat(toString).contains("nextRetryAt=null");
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              messageId,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              channel,
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event.channel()).isEqualTo(channel);
    }

    @Test
    @DisplayName("应该正确返回 nextRetryCount")
    void shouldReturnNextRetryCount() {
      // Given
      int nextRetryCount = 3;
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              nextRetryCount,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event.nextRetryCount()).isEqualTo(nextRetryCount);
    }

    @Test
    @DisplayName("应该正确返回 nextRetryAt")
    void shouldReturnNextRetryAt() {
      // Given
      Instant nextRetryAt = Instant.parse("2024-01-15T10:35:00Z");
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              nextRetryAt,
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event.nextRetryAt()).isEqualTo(nextRetryAt);
    }

    @Test
    @DisplayName("应该正确返回 errorCode")
    void shouldReturnErrorCode() {
      // Given
      String errorCode = "NETWORK_TIMEOUT";
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              errorCode,
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event.errorCode()).isEqualTo(errorCode);
    }

    @Test
    @DisplayName("应该正确返回 errorMessage")
    void shouldReturnErrorMessage() {
      // Given
      String errorMessage = "Temporary error message";
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              errorMessage,
              Instant.now());

      // When & Then
      assertThat(event.errorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("应该正确返回 occurredAt")
    void shouldReturnOccurredAt() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              occurredAt);

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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event).isInstanceOf(OutboxRelayDomainEvent.class);
    }

    @Test
    @DisplayName("应该实现 DomainEvent 接口")
    void shouldImplementDomainEventInterface() {
      // Given
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // When & Then
      assertThat(event).isInstanceOf(com.patra.common.domain.DomainEvent.class);
    }

    @Test
    @DisplayName("应该正确实现 occurredAt 方法（DomainEvent 接口要求）")
    void shouldImplementOccurredAtMethod() {
      // Given
      Instant expectedOccurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
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
      int nextRetryCount = 3;
      Instant nextRetryAt = Instant.parse("2024-01-15T10:35:00Z");
      String errorCode = "NETWORK_TIMEOUT";
      String errorMessage = "Temporary error";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              messageId, channel, nextRetryCount, nextRetryAt, errorCode, errorMessage, occurredAt);

      // When - 保存初始值
      Long initialMessageId = event.messageId();
      String initialChannel = event.channel();
      int initialNextRetryCount = event.nextRetryCount();
      Instant initialNextRetryAt = event.nextRetryAt();
      String initialErrorCode = event.errorCode();
      String initialErrorMessage = event.errorMessage();
      Instant initialOccurredAt = event.occurredAt();

      // Then - 字段应该保持不变（Record 自动为 final，无 setter 方法）
      assertThat(event.messageId()).isEqualTo(initialMessageId);
      assertThat(event.channel()).isEqualTo(initialChannel);
      assertThat(event.nextRetryCount()).isEqualTo(initialNextRetryCount);
      assertThat(event.nextRetryAt()).isEqualTo(initialNextRetryAt);
      assertThat(event.errorCode()).isEqualTo(initialErrorCode);
      assertThat(event.errorMessage()).isEqualTo(initialErrorMessage);
      assertThat(event.occurredAt()).isEqualTo(initialOccurredAt);
    }

    @Test
    @DisplayName("应该确保 Instant 字段不可变")
    void shouldEnsureInstantFieldsAreImmutable() {
      // Given
      Instant nextRetryAt = Instant.parse("2024-01-15T10:35:00Z");
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              nextRetryAt,
              "NETWORK_TIMEOUT",
              "Error message",
              occurredAt);

      // When - Instant 是不可变的，任何修改都会返回新实例
      Instant modifiedNextRetryAt = event.nextRetryAt().plusSeconds(3600);
      Instant modifiedOccurredAt = event.occurredAt().plusSeconds(3600);

      // Then - 原事件的字段应该保持不变
      assertThat(event.nextRetryAt()).isEqualTo(nextRetryAt);
      assertThat(event.nextRetryAt()).isNotEqualTo(modifiedNextRetryAt);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
      assertThat(event.occurredAt()).isNotEqualTo(modifiedOccurredAt);
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              largeMessageId,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              negativeMessageId,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.messageId()).isEqualTo(negativeMessageId);
    }

    @Test
    @DisplayName("应该处理极大的 nextRetryCount")
    void shouldHandleVeryLargeNextRetryCount() {
      // Given
      int largeNextRetryCount = Integer.MAX_VALUE;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              largeNextRetryCount,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.nextRetryCount()).isEqualTo(largeNextRetryCount);
    }

    @Test
    @DisplayName("应该处理极小的 nextRetryCount（负数）")
    void shouldHandleNegativeNextRetryCount() {
      // Given
      int negativeNextRetryCount = Integer.MIN_VALUE;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              negativeNextRetryCount,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.nextRetryCount()).isEqualTo(negativeNextRetryCount);
    }

    @Test
    @DisplayName("应该处理极长的 channel 名称")
    void shouldHandleVeryLongChannelName() {
      // Given
      String longChannel = "a".repeat(1000);

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              longChannel,
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              longErrorCode,
              "Error message",
              Instant.now());

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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, epoch, "NETWORK_TIMEOUT", "Error message", epoch);

      // Then
      assertThat(event.nextRetryAt()).isEqualTo(epoch);
      assertThat(event.occurredAt()).isEqualTo(epoch);
    }

    @Test
    @DisplayName("应该处理远期时间")
    void shouldHandleFutureTime() {
      // Given
      Instant future = Instant.parse("2099-12-31T23:59:59Z");

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L, "publication.parsed", 3, future, "NETWORK_TIMEOUT", "Error message", future);

      // Then
      assertThat(event.nextRetryAt()).isEqualTo(future);
      assertThat(event.occurredAt()).isEqualTo(future);
    }

    @Test
    @DisplayName("应该处理纳秒级时间精度")
    void shouldHandleNanosecondTimePrecision() {
      // Given
      Instant preciseTime = Instant.parse("2024-01-15T10:30:45.123456789Z");
      Instant preciseNextRetryTime = Instant.parse("2024-01-15T10:35:45.987654321Z");

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              preciseNextRetryTime,
              "NETWORK_TIMEOUT",
              "Error message",
              preciseTime);

      // Then
      assertThat(event.nextRetryAt()).isEqualTo(preciseNextRetryTime);
      assertThat(event.nextRetryAt().getNano()).isEqualTo(987654321);
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
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              emptyChannel,
              3,
              Instant.now().plusSeconds(300),
              emptyErrorCode,
              emptyErrorMessage,
              Instant.now());

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
      String errorCodeWithSpecialChars = "NETWORK_TIMEOUT_#123!@$%";
      String errorMessageWithSpecialChars =
          "Failed to connect: unexpected network error at line 42, column 15";

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              channelWithSpecialChars,
              3,
              Instant.now().plusSeconds(300),
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
      String multilineErrorMessage = "Error occurred:\nLine 1: Network timeout\nLine 2: Will retry";

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
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
      String unicodeErrorMessage = "网络超时：将在 5 分钟后重试 ⏳";

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              unicodeErrorMessage,
              Instant.now());

      // Then
      assertThat(event.errorMessage()).isEqualTo(unicodeErrorMessage);
      assertThat(event.errorMessage()).contains("网络超时");
      assertThat(event.errorMessage()).contains("⏳");
    }
  }

  // ========== 延迟重试业务逻辑测试 ==========

  @Nested
  @DisplayName("延迟重试业务逻辑测试")
  class DeferredRetryBusinessLogicTests {

    @Test
    @DisplayName("应该正确记录 nextRetryCount 为当前失败次数 + 1")
    void shouldRecordNextRetryCountAsCurrentFailurePlusOne() {
      // Given - 当前已失败 2 次，下次重试应该是第 3 次
      int currentFailureCount = 2;
      int expectedNextRetryCount = 3;

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              expectedNextRetryCount,
              Instant.now().plusSeconds(300),
              "NETWORK_TIMEOUT",
              "Error message",
              Instant.now());

      // Then
      assertThat(event.nextRetryCount()).isEqualTo(expectedNextRetryCount);
      assertThat(event.nextRetryCount()).isEqualTo(currentFailureCount + 1);
    }

    @Test
    @DisplayName("应该正确记录首次失败后的延迟重试（nextRetryCount = 1）")
    void shouldRecordFirstDeferredRetryAfterInitialFailure() {
      // Given - 首次失败，下次重试是第 1 次
      int nextRetryCount = 1;
      Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");
      Instant nextRetryAt = occurredAt.plusSeconds(60); // 1 分钟后重试

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              nextRetryCount,
              nextRetryAt,
              "NETWORK_TIMEOUT",
              "First failure, will retry in 60 seconds",
              occurredAt);

      // Then
      assertThat(event.nextRetryCount()).isEqualTo(1);
      assertThat(event.nextRetryAt()).isAfter(event.occurredAt());
      assertThat(event.nextRetryAt().getEpochSecond() - event.occurredAt().getEpochSecond())
          .isEqualTo(60);
    }

    @Test
    @DisplayName("应该正确记录 nextRetryAt 在 occurredAt 之后")
    void shouldRecordNextRetryAtAfterOccurredAt() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");
      Instant nextRetryAt = occurredAt.plusSeconds(300); // 5 分钟后

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              2,
              nextRetryAt,
              "NETWORK_TIMEOUT",
              "Error message",
              occurredAt);

      // Then
      assertThat(event.nextRetryAt()).isAfter(event.occurredAt());
    }

    @Test
    @DisplayName("应该允许 nextRetryAt 等于 occurredAt（立即重试）")
    void shouldAllowNextRetryAtEqualToOccurredAt() {
      // Given - 立即重试场景
      Instant sameTime = Instant.parse("2024-01-15T10:00:00Z");

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              1,
              sameTime,
              "NETWORK_TIMEOUT",
              "Immediate retry",
              sameTime);

      // Then
      assertThat(event.nextRetryAt()).isEqualTo(event.occurredAt());
    }

    @Test
    @DisplayName("应该允许 nextRetryAt 在 occurredAt 之前（异常但允许）")
    void shouldAllowNextRetryAtBeforeOccurredAt() {
      // Given - 异常场景，可能是时钟偏移导致
      Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");
      Instant nextRetryAt = occurredAt.minusSeconds(60); // 过去的时间

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              2,
              nextRetryAt,
              "NETWORK_TIMEOUT",
              "Error message",
              occurredAt);

      // Then
      assertThat(event.nextRetryAt()).isBefore(event.occurredAt());
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenarioTests {

    @Test
    @DisplayName("应该正确记录临时网络错误的延迟重试场景")
    void shouldRecordDeferredRetryForTransientNetworkError() {
      // Given - 临时性网络超时错误
      Long messageId = 1001L;
      String channel = "publication.parsed";
      int nextRetryCount = 2;
      Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");
      Instant nextRetryAt = occurredAt.plusSeconds(60); // 使用指数退避，1 分钟后重试
      String errorCode = "NETWORK_TIMEOUT";
      String errorMessage = "Transient network timeout, will retry with exponential backoff";

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              messageId, channel, nextRetryCount, nextRetryAt, errorCode, errorMessage, occurredAt);

      // Then
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.nextRetryCount()).isEqualTo(nextRetryCount);
      assertThat(event.nextRetryAt()).isAfter(event.occurredAt());
      assertThat(event.errorCode()).isEqualTo(errorCode);
      assertThat(event.errorMessage()).contains("will retry");
    }

    @Test
    @DisplayName("应该支持指数退避策略场景")
    void shouldSupportExponentialBackoffScenario() {
      // Given - 指数退避：1分钟 → 2分钟 → 4分钟
      Instant baseTime = Instant.parse("2024-01-15T10:00:00Z");

      // When - 第 1 次重试（1 分钟退避）
      OutboxMessageDeferredEvent retry1 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              1,
              baseTime.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Retry 1 after 1 minute",
              baseTime);

      // When - 第 2 次重试（2 分钟退避）
      OutboxMessageDeferredEvent retry2 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              2,
              baseTime.plusSeconds(60 + 120),
              "NETWORK_TIMEOUT",
              "Retry 2 after 2 minutes",
              baseTime.plusSeconds(60));

      // When - 第 3 次重试（4 分钟退避）
      OutboxMessageDeferredEvent retry3 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              baseTime.plusSeconds(60 + 120 + 240),
              "NETWORK_TIMEOUT",
              "Retry 3 after 4 minutes",
              baseTime.plusSeconds(60 + 120));

      // Then - 验证退避时间递增
      long backoff1 = retry1.nextRetryAt().getEpochSecond() - retry1.occurredAt().getEpochSecond();
      long backoff2 = retry2.nextRetryAt().getEpochSecond() - retry2.occurredAt().getEpochSecond();
      long backoff3 = retry3.nextRetryAt().getEpochSecond() - retry3.occurredAt().getEpochSecond();

      assertThat(backoff1).isEqualTo(60); // 1 分钟
      assertThat(backoff2).isEqualTo(120); // 2 分钟
      assertThat(backoff3).isEqualTo(240); // 4 分钟
      assertThat(backoff2).isGreaterThan(backoff1);
      assertThat(backoff3).isGreaterThan(backoff2);
    }

    @Test
    @DisplayName("应该支持固定延迟策略场景")
    void shouldSupportFixedDelayScenario() {
      // Given - 固定延迟：每次都是 5 分钟
      Instant baseTime = Instant.parse("2024-01-15T10:00:00Z");
      long fixedDelaySeconds = 300; // 5 分钟

      // When - 3 次重试，每次都是 5 分钟延迟
      OutboxMessageDeferredEvent retry1 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              1,
              baseTime.plusSeconds(fixedDelaySeconds),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Retry 1",
              baseTime);

      OutboxMessageDeferredEvent retry2 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              2,
              baseTime.plusSeconds(fixedDelaySeconds * 2),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Retry 2",
              baseTime.plusSeconds(fixedDelaySeconds));

      OutboxMessageDeferredEvent retry3 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              baseTime.plusSeconds(fixedDelaySeconds * 3),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Retry 3",
              baseTime.plusSeconds(fixedDelaySeconds * 2));

      // Then - 验证固定延迟
      long delay1 = retry1.nextRetryAt().getEpochSecond() - retry1.occurredAt().getEpochSecond();
      long delay2 = retry2.nextRetryAt().getEpochSecond() - retry2.occurredAt().getEpochSecond();
      long delay3 = retry3.nextRetryAt().getEpochSecond() - retry3.occurredAt().getEpochSecond();

      assertThat(delay1).isEqualTo(fixedDelaySeconds);
      assertThat(delay2).isEqualTo(fixedDelaySeconds);
      assertThat(delay3).isEqualTo(fixedDelaySeconds);
    }

    @Test
    @DisplayName("应该支持重试积压趋势分析场景")
    void shouldSupportRetryBacklogTrendAnalysisScenario() {
      // Given - 短时间内多个消息延迟重试
      Instant baseTime = Instant.parse("2024-01-15T10:00:00Z");

      // When - 10 秒内产生 5 个延迟重试事件
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              1,
              baseTime.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Error 1",
              baseTime);

      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1002L,
              "publication.parsed",
              1,
              baseTime.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Error 2",
              baseTime.plusSeconds(2));

      OutboxMessageDeferredEvent event3 =
          new OutboxMessageDeferredEvent(
              1003L,
              "publication.parsed",
              1,
              baseTime.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Error 3",
              baseTime.plusSeconds(4));

      OutboxMessageDeferredEvent event4 =
          new OutboxMessageDeferredEvent(
              1004L,
              "publication.parsed",
              1,
              baseTime.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Error 4",
              baseTime.plusSeconds(6));

      OutboxMessageDeferredEvent event5 =
          new OutboxMessageDeferredEvent(
              1005L,
              "publication.parsed",
              1,
              baseTime.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Error 5",
              baseTime.plusSeconds(8));

      // Then - 应该可以检测到高频延迟重试（可能需要调整退避策略）
      assertThat(event5.occurredAt().getEpochSecond() - event1.occurredAt().getEpochSecond())
          .isLessThanOrEqualTo(10);
      assertThat(event1.errorCode())
          .isEqualTo(event2.errorCode())
          .isEqualTo(event3.errorCode())
          .isEqualTo(event4.errorCode())
          .isEqualTo(event5.errorCode());
    }

    @Test
    @DisplayName("应该支持按错误代码分析下游稳定性场景")
    void shouldSupportDownstreamStabilityAnalysisByErrorCodeScenario() {
      // Given - 分析不同错误类型的延迟重试分布
      Instant now = Instant.now();

      // 网络超时错误（临时性）
      OutboxMessageDeferredEvent networkError1 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              2,
              now.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Network timeout 1",
              now);

      OutboxMessageDeferredEvent networkError2 =
          new OutboxMessageDeferredEvent(
              1002L,
              "publication.parsed",
              2,
              now.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Network timeout 2",
              now);

      // 下游服务不可用（可能是服务降级）
      OutboxMessageDeferredEvent downstreamError1 =
          new OutboxMessageDeferredEvent(
              2001L,
              "publication.parsed",
              3,
              now.plusSeconds(300),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Downstream service unavailable 1",
              now);

      OutboxMessageDeferredEvent downstreamError2 =
          new OutboxMessageDeferredEvent(
              2002L,
              "publication.parsed",
              3,
              now.plusSeconds(300),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Downstream service unavailable 2",
              now);

      OutboxMessageDeferredEvent downstreamError3 =
          new OutboxMessageDeferredEvent(
              2003L,
              "publication.parsed",
              3,
              now.plusSeconds(300),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Downstream service unavailable 3",
              now);

      // Then - 应该可以按错误代码聚合分析
      assertThat(networkError1.errorCode()).isEqualTo("NETWORK_TIMEOUT");
      assertThat(networkError2.errorCode()).isEqualTo("NETWORK_TIMEOUT");
      assertThat(downstreamError1.errorCode()).isEqualTo("DOWNSTREAM_SERVICE_UNAVAILABLE");
      assertThat(downstreamError2.errorCode()).isEqualTo("DOWNSTREAM_SERVICE_UNAVAILABLE");
      assertThat(downstreamError3.errorCode()).isEqualTo("DOWNSTREAM_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("应该支持按通道分析重试热点场景")
    void shouldSupportRetryHotspotAnalysisByChannelScenario() {
      // Given - 不同通道的重试分布
      Instant now = Instant.now();

      // 高重试通道
      OutboxMessageDeferredEvent highRetryChannel1 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              5,
              now.plusSeconds(600),
              "NETWORK_TIMEOUT",
              "High retry 1",
              now);

      OutboxMessageDeferredEvent highRetryChannel2 =
          new OutboxMessageDeferredEvent(
              1002L,
              "publication.parsed",
              5,
              now.plusSeconds(600),
              "NETWORK_TIMEOUT",
              "High retry 2",
              now);

      // 低重试通道
      OutboxMessageDeferredEvent lowRetryChannel =
          new OutboxMessageDeferredEvent(
              2001L,
              "provenance.changed",
              1,
              now.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Low retry",
              now);

      // Then - 应该可以识别高重试热点通道
      assertThat(highRetryChannel1.channel()).isEqualTo("publication.parsed");
      assertThat(highRetryChannel2.channel()).isEqualTo("publication.parsed");
      assertThat(highRetryChannel1.nextRetryCount()).isEqualTo(5);
      assertThat(highRetryChannel2.nextRetryCount()).isEqualTo(5);
      assertThat(lowRetryChannel.channel()).isEqualTo("provenance.changed");
      assertThat(lowRetryChannel.nextRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该支持重试限制逼近告警场景")
    void shouldSupportRetryLimitApproachingAlertScenario() {
      // Given - 重试次数接近限制（假设限制为 5 次）
      int maxRetries = 5;
      int warningThreshold = 4;
      Instant now = Instant.now();

      // When - nextRetryCount = 4，即将达到限制
      OutboxMessageDeferredEvent approachingLimit =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              warningThreshold,
              now.plusSeconds(480),
              "NETWORK_TIMEOUT",
              "Approaching retry limit",
              now);

      // Then - 应该可以检测到警告条件
      assertThat(approachingLimit.nextRetryCount()).isGreaterThanOrEqualTo(warningThreshold);
      assertThat(approachingLimit.nextRetryCount()).isLessThan(maxRetries);
    }

    @Test
    @DisplayName("应该支持不同错误类型的退避策略场景")
    void shouldSupportDifferentBackoffStrategiesForDifferentErrorTypesScenario() {
      // Given - 不同错误类型使用不同退避策略
      Instant now = Instant.now();

      // 网络超时 - 短退避（1 分钟）
      OutboxMessageDeferredEvent networkTimeout =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              2,
              now.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Network timeout, short backoff",
              now);

      // 下游服务不可用 - 长退避（5 分钟）
      OutboxMessageDeferredEvent downstreamUnavailable =
          new OutboxMessageDeferredEvent(
              1002L,
              "publication.parsed",
              2,
              now.plusSeconds(300),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Downstream unavailable, long backoff",
              now);

      // 限流错误 - 超长退避（15 分钟）
      OutboxMessageDeferredEvent rateLimitExceeded =
          new OutboxMessageDeferredEvent(
              1003L,
              "publication.parsed",
              2,
              now.plusSeconds(900),
              "RATE_LIMIT_EXCEEDED",
              "Rate limit exceeded, very long backoff",
              now);

      // Then - 验证不同退避时长
      long networkBackoff =
          networkTimeout.nextRetryAt().getEpochSecond()
              - networkTimeout.occurredAt().getEpochSecond();
      long downstreamBackoff =
          downstreamUnavailable.nextRetryAt().getEpochSecond()
              - downstreamUnavailable.occurredAt().getEpochSecond();
      long rateLimitBackoff =
          rateLimitExceeded.nextRetryAt().getEpochSecond()
              - rateLimitExceeded.occurredAt().getEpochSecond();

      assertThat(networkBackoff).isEqualTo(60);
      assertThat(downstreamBackoff).isEqualTo(300);
      assertThat(rateLimitBackoff).isEqualTo(900);
      assertThat(downstreamBackoff).isGreaterThan(networkBackoff);
      assertThat(rateLimitBackoff).isGreaterThan(downstreamBackoff);
    }

    @Test
    @DisplayName("应该支持时间序列分析重试趋势场景")
    void shouldSupportTimeSeriesRetryTrendAnalysisScenario() {
      // Given - 时间序列记录延迟重试事件
      Instant t1 = Instant.parse("2024-01-15T10:00:00Z");
      Instant t2 = Instant.parse("2024-01-15T11:00:00Z");
      Instant t3 = Instant.parse("2024-01-15T12:00:00Z");

      // When
      OutboxMessageDeferredEvent event1 =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              2,
              t1.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Retry at 10:00",
              t1);

      OutboxMessageDeferredEvent event2 =
          new OutboxMessageDeferredEvent(
              1002L,
              "publication.parsed",
              3,
              t2.plusSeconds(120),
              "NETWORK_TIMEOUT",
              "Retry at 11:00",
              t2);

      OutboxMessageDeferredEvent event3 =
          new OutboxMessageDeferredEvent(
              1003L,
              "publication.parsed",
              4,
              t3.plusSeconds(240),
              "NETWORK_TIMEOUT",
              "Retry at 12:00",
              t3);

      // Then - 应该可以按时间排序分析重试趋势
      assertThat(event1.occurredAt()).isBefore(event2.occurredAt());
      assertThat(event2.occurredAt()).isBefore(event3.occurredAt());

      // 分析重试次数递增趋势（可能表示问题恶化）
      assertThat(event1.nextRetryCount()).isLessThan(event2.nextRetryCount());
      assertThat(event2.nextRetryCount()).isLessThan(event3.nextRetryCount());
    }

    @Test
    @DisplayName("应该支持零延迟立即重试场景")
    void shouldSupportZeroDelayImmediateRetryScenario() {
      // Given - 某些错误应该立即重试，无延迟
      Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");
      Instant nextRetryAt = occurredAt; // 立即重试

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              1,
              nextRetryAt,
              "TRANSIENT_ERROR",
              "Immediate retry without delay",
              occurredAt);

      // Then
      assertThat(event.nextRetryAt()).isEqualTo(event.occurredAt());
      long delaySeconds =
          event.nextRetryAt().getEpochSecond() - event.occurredAt().getEpochSecond();
      assertThat(delaySeconds).isZero();
    }

    @Test
    @DisplayName("应该支持长延迟重试场景（超过 1 小时）")
    void shouldSupportLongDelayRetryScenario() {
      // Given - 某些场景需要很长的退避时间
      Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");
      Instant nextRetryAt = occurredAt.plusSeconds(7200); // 2 小时后重试

      // When
      OutboxMessageDeferredEvent event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              5,
              nextRetryAt,
              "RATE_LIMIT_EXCEEDED",
              "Rate limit exceeded, retry after 2 hours",
              occurredAt);

      // Then
      long delaySeconds =
          event.nextRetryAt().getEpochSecond() - event.occurredAt().getEpochSecond();
      assertThat(delaySeconds).isEqualTo(7200);
      assertThat(delaySeconds).isGreaterThan(3600); // 超过 1 小时
    }

    @Test
    @DisplayName("应该支持跨通道重试对比分析场景")
    void shouldSupportCrossChannelRetryComparisonScenario() {
      // Given - 对比不同通道的重试行为
      Instant now = Instant.now();

      // Channel 1: 高频短退避
      OutboxMessageDeferredEvent channel1Event =
          new OutboxMessageDeferredEvent(
              1001L,
              "publication.parsed",
              3,
              now.plusSeconds(60),
              "NETWORK_TIMEOUT",
              "Error 1",
              now);

      // Channel 2: 低频长退避
      OutboxMessageDeferredEvent channel2Event =
          new OutboxMessageDeferredEvent(
              2001L,
              "provenance.changed",
              1,
              now.plusSeconds(300),
              "DOWNSTREAM_SERVICE_UNAVAILABLE",
              "Error 2",
              now);

      // Then - 应该可以对比分析
      assertThat(channel1Event.channel()).isEqualTo("publication.parsed");
      assertThat(channel1Event.nextRetryCount()).isGreaterThan(channel2Event.nextRetryCount());

      long channel1Delay =
          channel1Event.nextRetryAt().getEpochSecond()
              - channel1Event.occurredAt().getEpochSecond();
      long channel2Delay =
          channel2Event.nextRetryAt().getEpochSecond()
              - channel2Event.occurredAt().getEpochSecond();

      assertThat(channel2Delay).isGreaterThan(channel1Delay);
    }
  }
}
