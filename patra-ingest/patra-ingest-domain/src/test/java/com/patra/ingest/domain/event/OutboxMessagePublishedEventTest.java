package com.patra.ingest.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.domain.DomainEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OutboxMessagePublishedEvent 单元测试")
class OutboxMessagePublishedEventTest {

  @Nested
  @DisplayName("Record 构造与访问器测试")
  class RecordConstructionAndAccessorTests {

    @Test
    @DisplayName("应该正确构造事件并暴露所有字段")
    void shouldConstructEventAndExposeAllFields() {
      // Given: 准备测试数据
      Long messageId = 12345L;
      String channel = "publication.pubmed";
      String partitionKey = "partition-001";
      Instant occurredAt = Instant.parse("2025-01-05T10:15:30Z");

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(messageId, channel, partitionKey, occurredAt);

      // Then: 验证所有字段可访问
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.partitionKey()).isEqualTo(partitionKey);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("应该允许构造带有 null 字段的事件")
    void shouldAllowNullFields() {
      // Given: 准备包含 null 的数据
      Long messageId = null;
      String channel = null;
      String partitionKey = null;
      Instant occurredAt = null;

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(messageId, channel, partitionKey, occurredAt);

      // Then: 验证 null 值被保留
      assertThat(event.messageId()).isNull();
      assertThat(event.channel()).isNull();
      assertThat(event.partitionKey()).isNull();
      assertThat(event.occurredAt()).isNull();
    }

    @Test
    @DisplayName("应该处理空字符串字段")
    void shouldHandleEmptyStringFields() {
      // Given: 准备空字符串数据
      Long messageId = 1L;
      String channel = "";
      String partitionKey = "";
      Instant occurredAt = Instant.now();

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(messageId, channel, partitionKey, occurredAt);

      // Then: 验证空字符串被保留
      assertThat(event.channel()).isEmpty();
      assertThat(event.partitionKey()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Record 相等性与哈希码测试")
  class RecordEqualityAndHashCodeTests {

    @Test
    @DisplayName("相同值的两个事件应该相等")
    void twoEventsWithSameValuesShouldBeEqual() {
      // Given: 创建两个值相同的事件
      Long messageId = 999L;
      String channel = "publication.epmc";
      String partitionKey = "pk-123";
      Instant occurredAt = Instant.parse("2025-01-05T12:00:00Z");

      OutboxMessagePublishedEvent event1 =
          new OutboxMessagePublishedEvent(messageId, channel, partitionKey, occurredAt);
      OutboxMessagePublishedEvent event2 =
          new OutboxMessagePublishedEvent(messageId, channel, partitionKey, occurredAt);

      // Then: 验证相等性
      assertThat(event1).isEqualTo(event2);
      assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("不同 messageId 的事件不应相等")
    void eventsWithDifferentMessageIdShouldNotBeEqual() {
      // Given: 创建两个 messageId 不同的事件
      Instant now = Instant.now();
      OutboxMessagePublishedEvent event1 =
          new OutboxMessagePublishedEvent(1L, "channel", "key", now);
      OutboxMessagePublishedEvent event2 =
          new OutboxMessagePublishedEvent(2L, "channel", "key", now);

      // Then: 验证不相等
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 channel 的事件不应相等")
    void eventsWithDifferentChannelShouldNotBeEqual() {
      // Given: 创建两个 channel 不同的事件
      Instant now = Instant.now();
      OutboxMessagePublishedEvent event1 =
          new OutboxMessagePublishedEvent(1L, "channel1", "key", now);
      OutboxMessagePublishedEvent event2 =
          new OutboxMessagePublishedEvent(1L, "channel2", "key", now);

      // Then: 验证不相等
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 partitionKey 的事件不应相等")
    void eventsWithDifferentPartitionKeyShouldNotBeEqual() {
      // Given: 创建两个 partitionKey 不同的事件
      Instant now = Instant.now();
      OutboxMessagePublishedEvent event1 =
          new OutboxMessagePublishedEvent(1L, "channel", "key1", now);
      OutboxMessagePublishedEvent event2 =
          new OutboxMessagePublishedEvent(1L, "channel", "key2", now);

      // Then: 验证不相等
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 occurredAt 的事件不应相等")
    void eventsWithDifferentOccurredAtShouldNotBeEqual() {
      // Given: 创建两个 occurredAt 不同的事件
      OutboxMessagePublishedEvent event1 =
          new OutboxMessagePublishedEvent(
              1L, "channel", "key", Instant.parse("2025-01-05T10:00:00Z"));
      OutboxMessagePublishedEvent event2 =
          new OutboxMessagePublishedEvent(
              1L, "channel", "key", Instant.parse("2025-01-05T11:00:00Z"));

      // Then: 验证不相等
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("事件应该与自身相等")
    void eventShouldBeEqualToItself() {
      // Given: 创建一个事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(100L, "test-channel", "test-key", Instant.now());

      // Then: 验证与自身相等
      assertThat(event).isEqualTo(event);
      assertThat(event.hashCode()).isEqualTo(event.hashCode());
    }

    @Test
    @DisplayName("事件不应与 null 相等")
    void eventShouldNotBeEqualToNull() {
      // Given: 创建一个事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(100L, "test-channel", "test-key", Instant.now());

      // Then: 验证不等于 null
      assertThat(event).isNotEqualTo(null);
    }

    @Test
    @DisplayName("事件不应与不同类型的对象相等")
    void eventShouldNotBeEqualToDifferentType() {
      // Given: 创建一个事件和一个字符串对象
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(100L, "test-channel", "test-key", Instant.now());
      String differentType = "I am a string";

      // Then: 验证不相等
      assertThat(event).isNotEqualTo(differentType);
    }

    @Test
    @DisplayName("包含 null 字段的事件应该正确处理相等性")
    void eventsWithNullFieldsShouldHandleEqualityCorrectly() {
      // Given: 创建两个包含相同 null 字段的事件
      OutboxMessagePublishedEvent event1 = new OutboxMessagePublishedEvent(null, null, null, null);
      OutboxMessagePublishedEvent event2 = new OutboxMessagePublishedEvent(null, null, null, null);

      // Then: 验证相等性
      assertThat(event1).isEqualTo(event2);
      assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }
  }

  @Nested
  @DisplayName("Record toString 测试")
  class RecordToStringTests {

    @Test
    @DisplayName("toString 应该包含所有字段信息")
    void toStringShouldContainAllFieldInformation() {
      // Given: 创建一个事件
      Long messageId = 42L;
      String channel = "test.channel";
      String partitionKey = "pk-999";
      Instant occurredAt = Instant.parse("2025-01-05T14:30:00Z");

      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(messageId, channel, partitionKey, occurredAt);

      // When: 调用 toString
      String result = event.toString();

      // Then: 验证包含所有字段
      assertThat(result)
          .contains("OutboxMessagePublishedEvent")
          .contains("messageId=" + messageId)
          .contains("channel=" + channel)
          .contains("partitionKey=" + partitionKey)
          .contains("occurredAt=" + occurredAt);
    }

    @Test
    @DisplayName("toString 应该正确处理 null 字段")
    void toStringShouldHandleNullFieldsCorrectly() {
      // Given: 创建包含 null 字段的事件
      OutboxMessagePublishedEvent event = new OutboxMessagePublishedEvent(null, null, null, null);

      // When: 调用 toString
      String result = event.toString();

      // Then: 验证包含 null 标识
      assertThat(result).contains("OutboxMessagePublishedEvent").contains("null");
    }
  }

  @Nested
  @DisplayName("领域事件接口实现测试")
  class DomainEventInterfaceTests {

    @Test
    @DisplayName("应该实现 DomainEvent 接口")
    void shouldImplementDomainEventInterface() {
      // Given: 创建一个事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(1L, "channel", "key", Instant.now());

      // Then: 验证实现了 DomainEvent
      assertThat(event).isInstanceOf(DomainEvent.class);
    }

    @Test
    @DisplayName("应该实现 OutboxRelayDomainEvent 接口")
    void shouldImplementOutboxRelayDomainEventInterface() {
      // Given: 创建一个事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(1L, "channel", "key", Instant.now());

      // Then: 验证实现了 OutboxRelayDomainEvent
      assertThat(event).isInstanceOf(OutboxRelayDomainEvent.class);
    }

    @Test
    @DisplayName("occurredAt 方法应该正确返回时间戳")
    void occurredAtMethodShouldReturnCorrectTimestamp() {
      // Given: 准备时间戳
      Instant expectedTimestamp = Instant.parse("2025-01-05T08:00:00Z");

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(1L, "channel", "key", expectedTimestamp);

      // Then: 验证 occurredAt 方法返回正确值
      assertThat(event.occurredAt()).isEqualTo(expectedTimestamp);
    }
  }

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenarioTests {

    @Test
    @DisplayName("应该正确表示 PubMed 出版物发布事件")
    void shouldCorrectlyRepresentPubMedPublicationPublishedEvent() {
      // Given: PubMed 出版物发布场景
      Long messageId = 10001L;
      String channel = "publication.pubmed";
      String partitionKey = "PMID_38572234";
      Instant occurredAt = Instant.now();

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(messageId, channel, partitionKey, occurredAt);

      // Then: 验证事件字段
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.partitionKey()).startsWith("PMID_");
      assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("应该正确表示 EPMC 出版物发布事件")
    void shouldCorrectlyRepresentEpmcPublicationPublishedEvent() {
      // Given: EPMC 出版物发布场景
      Long messageId = 20001L;
      String channel = "publication.epmc";
      String partitionKey = "PMC_10925847";
      Instant occurredAt = Instant.now();

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(messageId, channel, partitionKey, occurredAt);

      // Then: 验证事件字段
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.partitionKey()).startsWith("PMC_");
      assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("应该支持批量消息的幂等性检查")
    void shouldSupportIdempotencyCheckForBatchMessages() {
      // Given: 创建模拟批量消息的多个事件
      Instant baseTime = Instant.parse("2025-01-05T10:00:00Z");
      OutboxMessagePublishedEvent event1 =
          new OutboxMessagePublishedEvent(1L, "channel", "key1", baseTime);
      OutboxMessagePublishedEvent event2 =
          new OutboxMessagePublishedEvent(2L, "channel", "key2", baseTime.plusSeconds(1));
      OutboxMessagePublishedEvent event3 =
          new OutboxMessagePublishedEvent(3L, "channel", "key3", baseTime.plusSeconds(2));

      // When: 使用 messageId 作为幂等性键
      Long dedupKey1 = event1.messageId();
      Long dedupKey2 = event2.messageId();
      Long dedupKey3 = event3.messageId();

      // Then: 验证每个事件有唯一的 messageId 用于幂等性
      assertThat(dedupKey1).isNotEqualTo(dedupKey2);
      assertThat(dedupKey2).isNotEqualTo(dedupKey3);
      assertThat(dedupKey1).isNotEqualTo(dedupKey3);
    }

    @Test
    @DisplayName("应该支持按 channel 分类进行指标统计")
    void shouldSupportMetricsGroupingByChannel() {
      // Given: 创建多个不同 channel 的事件
      Instant now = Instant.now();
      OutboxMessagePublishedEvent pubmedEvent =
          new OutboxMessagePublishedEvent(1L, "publication.pubmed", "key1", now);
      OutboxMessagePublishedEvent epmcEvent =
          new OutboxMessagePublishedEvent(2L, "publication.epmc", "key2", now);
      OutboxMessagePublishedEvent crossrefEvent =
          new OutboxMessagePublishedEvent(3L, "publication.crossref", "key3", now);

      // When: 按 channel 分组
      String pubmedChannel = pubmedEvent.channel();
      String epmcChannel = epmcEvent.channel();
      String crossrefChannel = crossrefEvent.channel();

      // Then: 验证可以区分不同 channel
      assertThat(pubmedChannel).isEqualTo("publication.pubmed");
      assertThat(epmcChannel).isEqualTo("publication.epmc");
      assertThat(crossrefChannel).isEqualTo("publication.crossref");
    }

    @Test
    @DisplayName("应该支持按 partitionKey 分析分区分布")
    void shouldSupportPartitionDistributionAnalysisByPartitionKey() {
      // Given: 创建多个相同 channel 但不同 partitionKey 的事件
      Instant now = Instant.now();
      String channel = "publication.pubmed";
      OutboxMessagePublishedEvent event1 =
          new OutboxMessagePublishedEvent(1L, channel, "partition-0", now);
      OutboxMessagePublishedEvent event2 =
          new OutboxMessagePublishedEvent(2L, channel, "partition-1", now);
      OutboxMessagePublishedEvent event3 =
          new OutboxMessagePublishedEvent(3L, channel, "partition-2", now);

      // When: 提取 partitionKey
      String pk1 = event1.partitionKey();
      String pk2 = event2.partitionKey();
      String pk3 = event3.partitionKey();

      // Then: 验证可以区分不同分区
      assertThat(pk1).isNotEqualTo(pk2);
      assertThat(pk2).isNotEqualTo(pk3);
      assertThat(pk1).isNotEqualTo(pk3);
    }
  }

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 应该是不可变的")
    void recordShouldBeImmutable() {
      // Given: 创建一个事件
      Long originalMessageId = 777L;
      String originalChannel = "original.channel";
      String originalPartitionKey = "original-key";
      Instant originalOccurredAt = Instant.parse("2025-01-05T09:00:00Z");

      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(
              originalMessageId, originalChannel, originalPartitionKey, originalOccurredAt);

      // When: 尝试修改外部引用（Record 内部已复制）
      Long modifiedMessageId = 999L;
      String modifiedChannel = "modified.channel";

      // Then: 验证事件内部状态未改变
      assertThat(event.messageId()).isEqualTo(originalMessageId);
      assertThat(event.channel()).isEqualTo(originalChannel);
      assertThat(event.partitionKey()).isEqualTo(originalPartitionKey);
      assertThat(event.occurredAt()).isEqualTo(originalOccurredAt);
    }

    @Test
    @DisplayName("多次调用访问器应该返回相同的值")
    void multipleAccessorCallsShouldReturnSameValue() {
      // Given: 创建一个事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(100L, "test-channel", "test-key", Instant.now());

      // When: 多次调用访问器
      Long messageId1 = event.messageId();
      Long messageId2 = event.messageId();
      String channel1 = event.channel();
      String channel2 = event.channel();
      String partitionKey1 = event.partitionKey();
      String partitionKey2 = event.partitionKey();
      Instant occurredAt1 = event.occurredAt();
      Instant occurredAt2 = event.occurredAt();

      // Then: 验证返回相同值
      assertThat(messageId1).isSameAs(messageId2);
      assertThat(channel1).isSameAs(channel2);
      assertThat(partitionKey1).isSameAs(partitionKey2);
      assertThat(occurredAt1).isSameAs(occurredAt2);
    }
  }

  @Nested
  @DisplayName("序列化测试")
  class SerializationTests {

    @Test
    @DisplayName("事件应该可以成功序列化和反序列化")
    void eventShouldBeSerializableAndDeserializable() throws Exception {
      // Given: 创建一个事件
      OutboxMessagePublishedEvent originalEvent =
          new OutboxMessagePublishedEvent(
              12345L, "publication.pubmed", "PMID_38572234", Instant.parse("2025-01-05T10:15:30Z"));

      // When: 序列化
      byte[] serialized;
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(baos)) {
        oos.writeObject(originalEvent);
        serialized = baos.toByteArray();
      }

      // And: 反序列化
      OutboxMessagePublishedEvent deserializedEvent;
      try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
          ObjectInputStream ois = new ObjectInputStream(bais)) {
        deserializedEvent = (OutboxMessagePublishedEvent) ois.readObject();
      }

      // Then: 验证反序列化后的对象与原始对象相等
      assertThat(deserializedEvent).isEqualTo(originalEvent);
      assertThat(deserializedEvent.messageId()).isEqualTo(originalEvent.messageId());
      assertThat(deserializedEvent.channel()).isEqualTo(originalEvent.channel());
      assertThat(deserializedEvent.partitionKey()).isEqualTo(originalEvent.partitionKey());
      assertThat(deserializedEvent.occurredAt()).isEqualTo(originalEvent.occurredAt());
    }

    @Test
    @DisplayName("包含 null 字段的事件应该可以序列化")
    void eventWithNullFieldsShouldBeSerializable() throws Exception {
      // Given: 创建包含 null 字段的事件
      OutboxMessagePublishedEvent originalEvent =
          new OutboxMessagePublishedEvent(null, null, null, null);

      // When: 序列化并反序列化
      byte[] serialized;
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(baos)) {
        oos.writeObject(originalEvent);
        serialized = baos.toByteArray();
      }

      OutboxMessagePublishedEvent deserializedEvent;
      try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
          ObjectInputStream ois = new ObjectInputStream(bais)) {
        deserializedEvent = (OutboxMessagePublishedEvent) ois.readObject();
      }

      // Then: 验证反序列化后的对象与原始对象相等
      assertThat(deserializedEvent).isEqualTo(originalEvent);
      assertThat(deserializedEvent.messageId()).isNull();
      assertThat(deserializedEvent.channel()).isNull();
      assertThat(deserializedEvent.partitionKey()).isNull();
      assertThat(deserializedEvent.occurredAt()).isNull();
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理最大 Long 值的 messageId")
    void shouldHandleMaxLongValueForMessageId() {
      // Given: 使用最大 Long 值
      Long maxMessageId = Long.MAX_VALUE;

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(maxMessageId, "channel", "key", Instant.now());

      // Then: 验证可以正确存储
      assertThat(event.messageId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理最小 Long 值的 messageId")
    void shouldHandleMinLongValueForMessageId() {
      // Given: 使用最小 Long 值
      Long minMessageId = Long.MIN_VALUE;

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(minMessageId, "channel", "key", Instant.now());

      // Then: 验证可以正确存储
      assertThat(event.messageId()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    @DisplayName("应该处理极长的 channel 字符串")
    void shouldHandleVeryLongChannelString() {
      // Given: 创建超长字符串
      String veryLongChannel = "a".repeat(10000);

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(1L, veryLongChannel, "key", Instant.now());

      // Then: 验证可以正确存储
      assertThat(event.channel()).hasSize(10000);
      assertThat(event.channel()).isEqualTo(veryLongChannel);
    }

    @Test
    @DisplayName("应该处理特殊字符的 channel 和 partitionKey")
    void shouldHandleSpecialCharactersInChannelAndPartitionKey() {
      // Given: 准备包含特殊字符的数据
      String channelWithSpecialChars = "channel.with-special_chars/测试@#$%";
      String partitionKeyWithSpecialChars = "key:with|special<chars>测试";

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(
              1L, channelWithSpecialChars, partitionKeyWithSpecialChars, Instant.now());

      // Then: 验证特殊字符被保留
      assertThat(event.channel()).isEqualTo(channelWithSpecialChars);
      assertThat(event.partitionKey()).isEqualTo(partitionKeyWithSpecialChars);
    }

    @Test
    @DisplayName("应该处理 Instant 最小值")
    void shouldHandleInstantMinValue() {
      // Given: 使用 Instant 最小值
      Instant minInstant = Instant.MIN;

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(1L, "channel", "key", minInstant);

      // Then: 验证可以正确存储
      assertThat(event.occurredAt()).isEqualTo(Instant.MIN);
    }

    @Test
    @DisplayName("应该处理 Instant 最大值")
    void shouldHandleInstantMaxValue() {
      // Given: 使用 Instant 最大值
      Instant maxInstant = Instant.MAX;

      // When: 创建事件
      OutboxMessagePublishedEvent event =
          new OutboxMessagePublishedEvent(1L, "channel", "key", maxInstant);

      // Then: 验证可以正确存储
      assertThat(event.occurredAt()).isEqualTo(Instant.MAX);
    }
  }
}
