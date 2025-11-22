package com.patra.ingest.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.domain.DomainEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OutboxRelayDomainEvent 接口契约测试。
///
/// 测试策略：
///
/// - 接口继承关系验证（DomainEvent、Serializable）
///   - 使用真实实现类测试接口契约
///   - 验证 occurredAt() 方法契约
///   - 验证序列化能力
///   - 测试多态性
///
@DisplayName("OutboxRelayDomainEvent 接口契约测试")
class OutboxRelayDomainEventTest {

  @Nested
  @DisplayName("接口继承关系测试")
  class InterfaceInheritanceTest {

    @Test
    @DisplayName("应该继承自 DomainEvent 接口")
    void shouldExtendDomainEvent() {
      // Given
      Class<OutboxRelayDomainEvent> eventInterface = OutboxRelayDomainEvent.class;

      // When & Then
      assertThat(DomainEvent.class.isAssignableFrom(eventInterface))
          .as("OutboxRelayDomainEvent 应该继承自 DomainEvent")
          .isTrue();
    }

    @Test
    @DisplayName("应该间接继承 Serializable 接口")
    void shouldImplementSerializable() {
      // Given
      Class<OutboxRelayDomainEvent> eventInterface = OutboxRelayDomainEvent.class;

      // When & Then
      assertThat(Serializable.class.isAssignableFrom(eventInterface))
          .as("OutboxRelayDomainEvent 通过 DomainEvent 间接继承 Serializable")
          .isTrue();
    }

    @Test
    @DisplayName("应该是接口类型")
    void shouldBeInterface() {
      // Given
      Class<OutboxRelayDomainEvent> eventInterface = OutboxRelayDomainEvent.class;

      // When & Then
      assertThat(eventInterface.isInterface()).as("OutboxRelayDomainEvent 应该是接口").isTrue();
    }
  }

  @Nested
  @DisplayName("接口方法契约测试")
  class InterfaceMethodContractTest {

    @Test
    @DisplayName("应该继承 occurredAt() 方法")
    void shouldInheritOccurredAtMethod() throws NoSuchMethodException {
      // Given
      Class<OutboxRelayDomainEvent> eventInterface = OutboxRelayDomainEvent.class;

      // When
      var method = eventInterface.getMethod("occurredAt");

      // Then
      assertThat(method).as("应该继承 occurredAt() 方法").isNotNull();
      assertThat(method.getReturnType())
          .as("occurredAt() 返回类型应该是 Instant")
          .isEqualTo(Instant.class);
    }

    @Test
    @DisplayName("occurredAt() 方法不应有参数")
    void occurredAtShouldHaveNoParameters() throws NoSuchMethodException {
      // Given
      Class<OutboxRelayDomainEvent> eventInterface = OutboxRelayDomainEvent.class;

      // When
      var method = eventInterface.getMethod("occurredAt");

      // Then
      assertThat(method.getParameterCount()).as("occurredAt() 方法不应有参数").isZero();
    }
  }

  @Nested
  @DisplayName("实现类契约测试 - OutboxMessagePublishedEvent")
  class PublishedEventContractTest {

    @Test
    @DisplayName("OutboxMessagePublishedEvent 应该实现 OutboxRelayDomainEvent 接口")
    void publishedEventShouldImplementInterface() {
      // Given
      var event =
          new OutboxMessagePublishedEvent(
              1L, "publication.ingested", "partition-key-1", Instant.now());

      // When & Then
      assertThat(event)
          .as("OutboxMessagePublishedEvent 应该是 OutboxRelayDomainEvent 的实例")
          .isInstanceOf(OutboxRelayDomainEvent.class);
      assertThat(event)
          .as("OutboxMessagePublishedEvent 应该是 DomainEvent 的实例")
          .isInstanceOf(DomainEvent.class);
    }

    @Test
    @DisplayName("应该正确实现 occurredAt() 方法")
    void shouldCorrectlyImplementOccurredAt() {
      // Given
      Instant expectedTime = Instant.parse("2025-01-15T10:30:00Z");
      var event =
          new OutboxMessagePublishedEvent(
              1L, "publication.ingested", "partition-key-1", expectedTime);

      // When
      Instant actualTime = event.occurredAt();

      // Then
      assertThat(actualTime).as("occurredAt() 应该返回构造时传入的时间戳").isEqualTo(expectedTime);
    }

    @Test
    @DisplayName("应该是不可变的 record 类型")
    void shouldBeImmutableRecord() {
      // Given
      var event =
          new OutboxMessagePublishedEvent(
              1L, "publication.ingested", "partition-key-1", Instant.now());

      // When & Then
      assertThat(event.getClass().isRecord())
          .as("OutboxMessagePublishedEvent 应该是 record 类型")
          .isTrue();
    }

    @Test
    @DisplayName("相同内容的事件应该相等")
    void eventsWithSameContentShouldBeEqual() {
      // Given
      Instant occurredAt = Instant.parse("2025-01-15T10:30:00Z");
      var event1 =
          new OutboxMessagePublishedEvent(
              1L, "publication.ingested", "partition-key-1", occurredAt);
      var event2 =
          new OutboxMessagePublishedEvent(
              1L, "publication.ingested", "partition-key-1", occurredAt);

      // When & Then
      assertThat(event1).as("相同内容的事件应该相等").isEqualTo(event2);
      assertThat(event1.hashCode()).as("相同内容的事件应该有相同的 hashCode").isEqualTo(event2.hashCode());
    }
  }

  @Nested
  @DisplayName("实现类契约测试 - OutboxMessageFailedEvent")
  class FailedEventContractTest {

    @Test
    @DisplayName("OutboxMessageFailedEvent 应该实现 OutboxRelayDomainEvent 接口")
    void failedEventShouldImplementInterface() {
      // Given
      var event =
          new OutboxMessageFailedEvent(
              1L,
              "publication.ingested",
              3,
              "DELIVERY_FAILED",
              "Failed to deliver message after 3 retries",
              Instant.now());

      // When & Then
      assertThat(event)
          .as("OutboxMessageFailedEvent 应该是 OutboxRelayDomainEvent 的实例")
          .isInstanceOf(OutboxRelayDomainEvent.class);
      assertThat(event)
          .as("OutboxMessageFailedEvent 应该是 DomainEvent 的实例")
          .isInstanceOf(DomainEvent.class);
    }

    @Test
    @DisplayName("应该正确实现 occurredAt() 方法")
    void shouldCorrectlyImplementOccurredAt() {
      // Given
      Instant expectedTime = Instant.parse("2025-01-15T10:35:00Z");
      var event =
          new OutboxMessageFailedEvent(
              1L,
              "publication.ingested",
              3,
              "DELIVERY_FAILED",
              "Failed to deliver message after 3 retries",
              expectedTime);

      // When
      Instant actualTime = event.occurredAt();

      // Then
      assertThat(actualTime).as("occurredAt() 应该返回构造时传入的时间戳").isEqualTo(expectedTime);
    }
  }

  @Nested
  @DisplayName("实现类契约测试 - OutboxLeaseMissedEvent")
  class LeaseMissedEventContractTest {

    @Test
    @DisplayName("OutboxLeaseMissedEvent 应该实现 OutboxRelayDomainEvent 接口")
    void leaseMissedEventShouldImplementInterface() {
      // Given
      var event =
          new OutboxLeaseMissedEvent(
              1L, "publication.ingested", "relay-instance-1", "relay-instance-2", Instant.now());

      // When & Then
      assertThat(event)
          .as("OutboxLeaseMissedEvent 应该是 OutboxRelayDomainEvent 的实例")
          .isInstanceOf(OutboxRelayDomainEvent.class);
      assertThat(event)
          .as("OutboxLeaseMissedEvent 应该是 DomainEvent 的实例")
          .isInstanceOf(DomainEvent.class);
    }

    @Test
    @DisplayName("应该正确实现 occurredAt() 方法")
    void shouldCorrectlyImplementOccurredAt() {
      // Given
      Instant expectedTime = Instant.parse("2025-01-15T10:40:00Z");
      var event =
          new OutboxLeaseMissedEvent(
              1L, "publication.ingested", "relay-instance-1", "relay-instance-2", expectedTime);

      // When
      Instant actualTime = event.occurredAt();

      // Then
      assertThat(actualTime).as("occurredAt() 应该返回构造时传入的时间戳").isEqualTo(expectedTime);
    }
  }

  @Nested
  @DisplayName("多态性测试")
  class PolymorphismTest {

    @Test
    @DisplayName("应该支持多态处理 - 作为 OutboxRelayDomainEvent")
    void shouldSupportPolymorphismAsOutboxRelayDomainEvent() {
      // Given
      Instant occurredAt = Instant.parse("2025-01-15T11:00:00Z");
      OutboxRelayDomainEvent event1 =
          new OutboxMessagePublishedEvent(1L, "channel-1", "key-1", occurredAt);
      OutboxRelayDomainEvent event2 =
          new OutboxMessageFailedEvent(2L, "channel-2", 3, "ERROR", "error msg", occurredAt);
      OutboxRelayDomainEvent event3 =
          new OutboxLeaseMissedEvent(3L, "channel-3", "owner-1", "owner-2", occurredAt);

      // When & Then
      assertThat(event1.occurredAt())
          .as("作为 OutboxRelayDomainEvent 类型仍然能访问 occurredAt()")
          .isEqualTo(occurredAt);
      assertThat(event2.occurredAt())
          .as("作为 OutboxRelayDomainEvent 类型仍然能访问 occurredAt()")
          .isEqualTo(occurredAt);
      assertThat(event3.occurredAt())
          .as("作为 OutboxRelayDomainEvent 类型仍然能访问 occurredAt()")
          .isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("应该支持多态处理 - 作为 DomainEvent")
    void shouldSupportPolymorphismAsDomainEvent() {
      // Given
      Instant occurredAt = Instant.parse("2025-01-15T11:05:00Z");
      DomainEvent event1 = new OutboxMessagePublishedEvent(1L, "channel-1", "key-1", occurredAt);
      DomainEvent event2 =
          new OutboxMessageFailedEvent(2L, "channel-2", 3, "ERROR", "error msg", occurredAt);

      // When & Then
      assertThat(event1)
          .as("作为 DomainEvent 类型仍然是 OutboxRelayDomainEvent 的实例")
          .isInstanceOf(OutboxRelayDomainEvent.class);
      assertThat(event2)
          .as("作为 DomainEvent 类型仍然是 OutboxRelayDomainEvent 的实例")
          .isInstanceOf(OutboxRelayDomainEvent.class);
    }

    @Test
    @DisplayName("应该能在集合中处理不同的实现类")
    void shouldHandleDifferentImplementationsInCollection() {
      // Given
      Instant occurredAt = Instant.parse("2025-01-15T11:10:00Z");
      var events =
          java.util.List.of(
              new OutboxMessagePublishedEvent(1L, "channel-1", "key-1", occurredAt),
              new OutboxMessageFailedEvent(2L, "channel-2", 3, "ERROR", "error msg", occurredAt),
              new OutboxLeaseMissedEvent(3L, "channel-3", "owner-1", "owner-2", occurredAt));

      // When
      long eventCount = events.stream().filter(e -> e.occurredAt().equals(occurredAt)).count();

      // Then
      assertThat(eventCount).as("应该能统一处理不同类型的 OutboxRelayDomainEvent").isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("序列化测试")
  class SerializationTest {

    @Test
    @DisplayName("OutboxMessagePublishedEvent 应该支持 Java 序列化")
    void publishedEventShouldSupportSerialization() throws Exception {
      // Given
      var originalEvent =
          new OutboxMessagePublishedEvent(
              1L, "publication.ingested", "partition-key-1", Instant.parse("2025-01-15T12:00:00Z"));

      // When - 序列化
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
        oos.writeObject(originalEvent);
      }

      // When - 反序列化
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      OutboxMessagePublishedEvent deserializedEvent;
      try (ObjectInputStream ois = new ObjectInputStream(bais)) {
        deserializedEvent = (OutboxMessagePublishedEvent) ois.readObject();
      }

      // Then
      assertThat(deserializedEvent).as("反序列化后的对象应该与原对象相等").isEqualTo(originalEvent);
      assertThat(deserializedEvent.occurredAt())
          .as("反序列化后的时间戳应该保持一致")
          .isEqualTo(originalEvent.occurredAt());
    }

    @Test
    @DisplayName("OutboxMessageFailedEvent 应该支持 Java 序列化")
    void failedEventShouldSupportSerialization() throws Exception {
      // Given
      var originalEvent =
          new OutboxMessageFailedEvent(
              1L,
              "publication.ingested",
              3,
              "DELIVERY_FAILED",
              "Failed to deliver message",
              Instant.parse("2025-01-15T12:05:00Z"));

      // When - 序列化
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
        oos.writeObject(originalEvent);
      }

      // When - 反序列化
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      OutboxMessageFailedEvent deserializedEvent;
      try (ObjectInputStream ois = new ObjectInputStream(bais)) {
        deserializedEvent = (OutboxMessageFailedEvent) ois.readObject();
      }

      // Then
      assertThat(deserializedEvent).as("反序列化后的对象应该与原对象相等").isEqualTo(originalEvent);
      assertThat(deserializedEvent.occurredAt())
          .as("反序列化后的时间戳应该保持一致")
          .isEqualTo(originalEvent.occurredAt());
    }

    @Test
    @DisplayName("OutboxLeaseMissedEvent 应该支持 Java 序列化")
    void leaseMissedEventShouldSupportSerialization() throws Exception {
      // Given
      var originalEvent =
          new OutboxLeaseMissedEvent(
              1L,
              "publication.ingested",
              "relay-instance-1",
              "relay-instance-2",
              Instant.parse("2025-01-15T12:10:00Z"));

      // When - 序列化
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
        oos.writeObject(originalEvent);
      }

      // When - 反序列化
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      OutboxLeaseMissedEvent deserializedEvent;
      try (ObjectInputStream ois = new ObjectInputStream(bais)) {
        deserializedEvent = (OutboxLeaseMissedEvent) ois.readObject();
      }

      // Then
      assertThat(deserializedEvent).as("反序列化后的对象应该与原对象相等").isEqualTo(originalEvent);
      assertThat(deserializedEvent.occurredAt())
          .as("反序列化后的时间戳应该保持一致")
          .isEqualTo(originalEvent.occurredAt());
    }
  }

  @Nested
  @DisplayName("边界值测试")
  class BoundaryValueTest {

    @Test
    @DisplayName("应该支持最小 messageId")
    void shouldSupportMinMessageId() {
      // Given
      Long minMessageId = Long.MIN_VALUE;
      var event = new OutboxMessagePublishedEvent(minMessageId, "channel", "key", Instant.now());

      // When & Then
      assertThat(event.messageId()).as("应该支持 Long 最小值作为 messageId").isEqualTo(minMessageId);
    }

    @Test
    @DisplayName("应该支持最大 messageId")
    void shouldSupportMaxMessageId() {
      // Given
      Long maxMessageId = Long.MAX_VALUE;
      var event = new OutboxMessagePublishedEvent(maxMessageId, "channel", "key", Instant.now());

      // When & Then
      assertThat(event.messageId()).as("应该支持 Long 最大值作为 messageId").isEqualTo(maxMessageId);
    }

    @Test
    @DisplayName("应该支持 Instant.MIN 作为时间戳")
    void shouldSupportMinInstant() {
      // Given
      Instant minInstant = Instant.MIN;
      var event = new OutboxMessagePublishedEvent(1L, "channel", "key", minInstant);

      // When & Then
      assertThat(event.occurredAt()).as("应该支持 Instant.MIN 作为时间戳").isEqualTo(minInstant);
    }

    @Test
    @DisplayName("应该支持 Instant.MAX 作为时间戳")
    void shouldSupportMaxInstant() {
      // Given
      Instant maxInstant = Instant.MAX;
      var event = new OutboxMessagePublishedEvent(1L, "channel", "key", maxInstant);

      // When & Then
      assertThat(event.occurredAt()).as("应该支持 Instant.MAX 作为时间戳").isEqualTo(maxInstant);
    }

    @Test
    @DisplayName("应该支持空字符串作为 channel")
    void shouldSupportEmptyChannel() {
      // Given
      var event = new OutboxMessagePublishedEvent(1L, "", "key", Instant.now());

      // When & Then
      assertThat(event.channel()).as("应该支持空字符串作为 channel").isEmpty();
    }

    @Test
    @DisplayName("应该支持重试次数为 0")
    void shouldSupportZeroRetryCount() {
      // Given
      var event =
          new OutboxMessageFailedEvent(
              1L, "channel", 0, "ERROR", "First attempt failed", Instant.now());

      // When & Then
      assertThat(event.retryCount()).as("应该支持重试次数为 0").isZero();
    }

    @Test
    @DisplayName("应该支持最大重试次数")
    void shouldSupportMaxRetryCount() {
      // Given
      int maxRetryCount = Integer.MAX_VALUE;
      var event =
          new OutboxMessageFailedEvent(
              1L, "channel", maxRetryCount, "ERROR", "Max retries exceeded", Instant.now());

      // When & Then
      assertThat(event.retryCount()).as("应该支持最大重试次数").isEqualTo(maxRetryCount);
    }
  }

  @Nested
  @DisplayName("toString() 方法测试")
  class ToStringTest {

    @Test
    @DisplayName("OutboxMessagePublishedEvent 的 toString() 应该包含所有字段")
    void publishedEventToStringShouldContainAllFields() {
      // Given
      var event =
          new OutboxMessagePublishedEvent(
              123L,
              "publication.ingested",
              "partition-key-1",
              Instant.parse("2025-01-15T13:00:00Z"));

      // When
      String result = event.toString();

      // Then
      assertThat(result)
          .as("toString() 应该包含 messageId")
          .contains("123")
          .contains("publication.ingested")
          .contains("partition-key-1")
          .contains("2025-01-15T13:00:00Z");
    }

    @Test
    @DisplayName("OutboxMessageFailedEvent 的 toString() 应该包含所有字段")
    void failedEventToStringShouldContainAllFields() {
      // Given
      var event =
          new OutboxMessageFailedEvent(
              456L,
              "publication.ingested",
              3,
              "DELIVERY_FAILED",
              "Network timeout",
              Instant.parse("2025-01-15T13:05:00Z"));

      // When
      String result = event.toString();

      // Then
      assertThat(result)
          .as("toString() 应该包含所有关键字段")
          .contains("456")
          .contains("publication.ingested")
          .contains("3")
          .contains("DELIVERY_FAILED")
          .contains("Network timeout")
          .contains("2025-01-15T13:05:00Z");
    }

    @Test
    @DisplayName("OutboxLeaseMissedEvent 的 toString() 应该包含所有字段")
    void leaseMissedEventToStringShouldContainAllFields() {
      // Given
      var event =
          new OutboxLeaseMissedEvent(
              789L,
              "publication.ingested",
              "relay-instance-1",
              "relay-instance-2",
              Instant.parse("2025-01-15T13:10:00Z"));

      // When
      String result = event.toString();

      // Then
      assertThat(result)
          .as("toString() 应该包含所有关键字段")
          .contains("789")
          .contains("publication.ingested")
          .contains("relay-instance-1")
          .contains("relay-instance-2")
          .contains("2025-01-15T13:10:00Z");
    }
  }
}
