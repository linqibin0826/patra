package com.patra.ingest.domain.model.vo.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.patra.ingest.domain.event.OutboxLeaseMissedEvent;
import com.patra.ingest.domain.event.OutboxMessageFailedEvent;
import com.patra.ingest.domain.event.OutboxMessagePublishedEvent;
import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import com.patra.ingest.domain.messaging.IngestPublishingChannels;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RelayBatchResult} 的单元测试。
 *
 * <p>测试策略:
 *
 * <ul>
 *   <li>Record 语义: equals/hashCode/toString
 *   <li>字段访问器: 所有字段的 getter
 *   <li>构造验证: 防御性复制和 null 处理
 *   <li>工厂方法: empty() 方法
 * </ul>
 */
@DisplayName("RelayBatchResult 测试")
class RelayBatchResultTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的 RelayBatchResult")
    void shouldCreateRelayBatchResultWithAllFields() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var events = createSampleEvents();

      // When
      var result =
          new RelayBatchResult(
              channel,
              10, // fetched
              8, // published
              1, // retried
              1, // failed
              2, // leaseMissed
              events);

      // Then
      assertThat(result.channel()).isEqualTo(channel);
      assertThat(result.fetched()).isEqualTo(10);
      assertThat(result.published()).isEqualTo(8);
      assertThat(result.retried()).isEqualTo(1);
      assertThat(result.failed()).isEqualTo(1);
      assertThat(result.leaseMissed()).isEqualTo(2);
      assertThat(result.events()).hasSize(3).containsExactlyElementsOf(events);
    }

    @Test
    @DisplayName("应该创建零计数的 RelayBatchResult")
    void shouldCreateRelayBatchResultWithZeroCounts() {
      // Given
      var channel = IngestPublishingChannels.LITERATURE_DATA_READY;

      // When
      var result = new RelayBatchResult(channel, 0, 0, 0, 0, 0, Collections.emptyList());

      // Then
      assertThat(result.channel()).isEqualTo(channel);
      assertThat(result.fetched()).isZero();
      assertThat(result.published()).isZero();
      assertThat(result.retried()).isZero();
      assertThat(result.failed()).isZero();
      assertThat(result.leaseMissed()).isZero();
      assertThat(result.events()).isEmpty();
    }

    @Test
    @DisplayName("应该接受 null events 并转换为空列表")
    void shouldHandleNullEventsAsEmptyList() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;

      // When
      var result = new RelayBatchResult(channel, 5, 5, 0, 0, 0, null);

      // Then
      assertThat(result.events()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("应该对 events 列表进行防御性复制")
    void shouldPerformDefensiveCopyOfEventsList() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var mutableEvents = new ArrayList<OutboxRelayDomainEvent>();
      mutableEvents.add(
          new OutboxMessagePublishedEvent(1L, "INGEST_TASK_READY", "partition-1", Instant.now()));

      // When
      var result = new RelayBatchResult(channel, 1, 1, 0, 0, 0, mutableEvents);
      var originalSize = result.events().size();

      // 修改原始列表
      mutableEvents.add(
          new OutboxMessagePublishedEvent(2L, "INGEST_TASK_READY", "partition-2", Instant.now()));

      // Then
      assertThat(result.events())
          .as("RelayBatchResult 的 events 应该是不可变的")
          .hasSize(originalSize)
          .hasSize(1);
    }

    @Test
    @DisplayName("应该返回不可修改的 events 列表")
    void shouldReturnUnmodifiableEventsList() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var events = createSampleEvents();
      var result = new RelayBatchResult(channel, 3, 3, 0, 0, 0, events);

      // When & Then
      assertThat(result.events())
          .as("返回的 events 列表应该是不可修改的")
          .isUnmodifiable()
          .hasSize(3);
    }
  }

  @Nested
  @DisplayName("字段访问器测试")
  class FieldAccessorTests {

    @Test
    @DisplayName("channel() 应该返回正确的通道键")
    void channelShouldReturnCorrectChannelKey() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var result = new RelayBatchResult(channel, 0, 0, 0, 0, 0, List.of());

      // When
      var actualChannel = result.channel();

      // Then
      assertThat(actualChannel)
          .isEqualTo(channel)
          .satisfies(
              ch -> {
                assertThat(ch.domain()).isEqualTo("INGEST");
                assertThat(ch.resource()).isEqualTo("TASK");
                assertThat(ch.event()).isEqualTo("READY");
              });
    }

    @Test
    @DisplayName("fetched() 应该返回获取的消息数")
    void fetchedShouldReturnFetchedCount() {
      // Given
      var result =
          new RelayBatchResult(IngestPublishingChannels.TASK_READY, 100, 95, 3, 2, 5, List.of());

      // When & Then
      assertThat(result.fetched()).isEqualTo(100);
    }

    @Test
    @DisplayName("published() 应该返回发布成功的消息数")
    void publishedShouldReturnPublishedCount() {
      // Given
      var result =
          new RelayBatchResult(IngestPublishingChannels.TASK_READY, 100, 95, 3, 2, 5, List.of());

      // When & Then
      assertThat(result.published()).isEqualTo(95);
    }

    @Test
    @DisplayName("retried() 应该返回重试的消息数")
    void retriedShouldReturnRetriedCount() {
      // Given
      var result =
          new RelayBatchResult(IngestPublishingChannels.TASK_READY, 100, 95, 3, 2, 5, List.of());

      // When & Then
      assertThat(result.retried()).isEqualTo(3);
    }

    @Test
    @DisplayName("failed() 应该返回失败的消息数")
    void failedShouldReturnFailedCount() {
      // Given
      var result =
          new RelayBatchResult(IngestPublishingChannels.TASK_READY, 100, 95, 3, 2, 5, List.of());

      // When & Then
      assertThat(result.failed()).isEqualTo(2);
    }

    @Test
    @DisplayName("leaseMissed() 应该返回租约丢失的消息数")
    void leaseMissedShouldReturnLeaseMissedCount() {
      // Given
      var result =
          new RelayBatchResult(IngestPublishingChannels.TASK_READY, 100, 95, 3, 2, 5, List.of());

      // When & Then
      assertThat(result.leaseMissed()).isEqualTo(5);
    }

    @Test
    @DisplayName("events() 应该返回领域事件列表")
    void eventsShouldReturnDomainEventsList() {
      // Given
      var events = createSampleEvents();
      var result =
          new RelayBatchResult(IngestPublishingChannels.TASK_READY, 3, 3, 0, 0, 0, events);

      // When
      var actualEvents = result.events();

      // Then
      assertThat(actualEvents)
          .hasSize(3)
          .extracting("class.simpleName")
          .containsExactly(
              "OutboxMessagePublishedEvent",
              "OutboxMessageFailedEvent",
              "OutboxLeaseMissedEvent");
    }

    @Test
    @DisplayName("events() 应该返回多个不同类型的领域事件")
    void eventsShouldReturnMultipleDifferentDomainEvents() {
      // Given
      var now = Instant.parse("2025-01-15T10:00:00Z");
      var events =
          List.<OutboxRelayDomainEvent>of(
              new OutboxMessagePublishedEvent(1L, "INGEST_TASK_READY", "key-1", now),
              new OutboxMessagePublishedEvent(2L, "INGEST_TASK_READY", "key-2", now),
              new OutboxMessageFailedEvent(
                  3L, "INGEST_TASK_READY", 3, "PUBLISH_ERROR", "Failed to publish", now),
              new OutboxLeaseMissedEvent(
                  4L, "INGEST_TASK_READY", "relay-1", "relay-2", now));

      var result =
          new RelayBatchResult(IngestPublishingChannels.TASK_READY, 4, 2, 0, 1, 1, events);

      // When
      var actualEvents = result.events();

      // Then
      assertThat(actualEvents).hasSize(4);
      assertThat(actualEvents)
          .filteredOn(e -> e instanceof OutboxMessagePublishedEvent)
          .hasSize(2);
      assertThat(actualEvents).filteredOn(e -> e instanceof OutboxMessageFailedEvent).hasSize(1);
      assertThat(actualEvents).filteredOn(e -> e instanceof OutboxLeaseMissedEvent).hasSize(1);
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("empty() 应该创建所有计数为零的空结果")
    void emptyShouldCreateResultWithZeroCounts() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;

      // When
      var result = RelayBatchResult.empty(channel);

      // Then
      assertThat(result.channel()).isEqualTo(channel);
      assertThat(result.fetched()).isZero();
      assertThat(result.published()).isZero();
      assertThat(result.retried()).isZero();
      assertThat(result.failed()).isZero();
      assertThat(result.leaseMissed()).isZero();
      assertThat(result.events()).isEmpty();
    }

    @Test
    @DisplayName("empty() 应该为不同通道创建独立的空结果")
    void emptyShouldCreateIndependentResultsForDifferentChannels() {
      // Given
      var channel1 = IngestPublishingChannels.TASK_READY;
      var channel2 = IngestPublishingChannels.LITERATURE_DATA_READY;

      // When
      var result1 = RelayBatchResult.empty(channel1);
      var result2 = RelayBatchResult.empty(channel2);

      // Then
      assertThat(result1.channel()).isEqualTo(channel1);
      assertThat(result2.channel()).isEqualTo(channel2);
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("empty() 应该返回不可修改的空事件列表")
    void emptyShouldReturnUnmodifiableEmptyEventsList() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;

      // When
      var result = RelayBatchResult.empty(channel);

      // Then
      assertThat(result.events()).isUnmodifiable().isEmpty();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("equals() 应该在所有字段相同时返回 true")
    void equalsShouldReturnTrueWhenAllFieldsMatch() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var events = createSampleEvents();

      var result1 = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events);
      var result2 = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events);

      // When & Then
      assertThat(result1).isEqualTo(result2);
      assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("equals() 应该在 channel 不同时返回 false")
    void equalsShouldReturnFalseWhenChannelDiffers() {
      // Given
      var events = List.<OutboxRelayDomainEvent>of();

      var result1 =
          new RelayBatchResult(IngestPublishingChannels.TASK_READY, 10, 8, 1, 1, 2, events);
      var result2 =
          new RelayBatchResult(
              IngestPublishingChannels.LITERATURE_DATA_READY, 10, 8, 1, 1, 2, events);

      // When & Then
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("equals() 应该在计数字段不同时返回 false")
    void equalsShouldReturnFalseWhenCountsDiffer() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var events = List.<OutboxRelayDomainEvent>of();

      var result1 = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events);
      var result2 = new RelayBatchResult(channel, 10, 7, 1, 1, 2, events); // published 不同

      // When & Then
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("equals() 应该在 events 不同时返回 false")
    void equalsShouldReturnFalseWhenEventsDiffer() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var events1 = createSampleEvents();
      var events2 = List.<OutboxRelayDomainEvent>of();

      var result1 = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events1);
      var result2 = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events2);

      // When & Then
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("equals() 应该在与自身比较时返回 true")
    void equalsShouldReturnTrueWhenComparingWithItself() {
      // Given
      var result =
          new RelayBatchResult(
              IngestPublishingChannels.TASK_READY, 10, 8, 1, 1, 2, createSampleEvents());

      // When & Then
      assertThat(result).isEqualTo(result);
    }

    @Test
    @DisplayName("equals() 应该在与 null 比较时返回 false")
    void equalsShouldReturnFalseWhenComparingWithNull() {
      // Given
      var result =
          new RelayBatchResult(
              IngestPublishingChannels.TASK_READY, 10, 8, 1, 1, 2, createSampleEvents());

      // When & Then
      assertThat(result).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() 应该在与不同类型对象比较时返回 false")
    void equalsShouldReturnFalseWhenComparingWithDifferentType() {
      // Given
      var result =
          new RelayBatchResult(
              IngestPublishingChannels.TASK_READY, 10, 8, 1, 1, 2, createSampleEvents());

      // When & Then
      assertThat(result).isNotEqualTo("not a RelayBatchResult");
    }

    @Test
    @DisplayName("hashCode() 应该对相同的对象返回相同的值")
    void hashCodeShouldReturnSameValueForEqualObjects() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var events = createSampleEvents();

      var result1 = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events);
      var result2 = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events);

      // When & Then
      assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("hashCode() 应该对不同的对象返回不同的值（高概率）")
    void hashCodeShouldReturnDifferentValueForDifferentObjects() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var events = createSampleEvents();

      var result1 = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events);
      var result2 = new RelayBatchResult(channel, 20, 18, 1, 1, 2, events);

      // When & Then
      assertThat(result1.hashCode()).isNotEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("toString() 应该包含所有字段信息")
    void toStringShouldContainAllFieldInformation() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var result = new RelayBatchResult(channel, 10, 8, 1, 1, 2, List.of());

      // When
      var toString = result.toString();

      // Then
      assertThat(toString)
          .contains("RelayBatchResult")
          .contains("channel=" + channel)
          .contains("fetched=10")
          .contains("published=8")
          .contains("retried=1")
          .contains("failed=1")
          .contains("leaseMissed=2")
          .contains("events=");
    }

    @Test
    @DisplayName("toString() 应该对空结果返回有效字符串")
    void toStringShouldReturnValidStringForEmptyResult() {
      // Given
      var result = RelayBatchResult.empty(IngestPublishingChannels.TASK_READY);

      // When
      var toString = result.toString();

      // Then
      assertThat(toString)
          .contains("RelayBatchResult")
          .contains("fetched=0")
          .contains("published=0")
          .contains("retried=0")
          .contains("failed=0")
          .contains("leaseMissed=0")
          .contains("events=[]");
    }
  }

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenarioTests {

    @Test
    @DisplayName("应该正确表示全部成功的批次")
    void shouldRepresentFullySuccessfulBatch() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var publishedEvents =
          List.<OutboxRelayDomainEvent>of(
              new OutboxMessagePublishedEvent(
                  1L, "INGEST_TASK_READY", "key-1", Instant.now()),
              new OutboxMessagePublishedEvent(
                  2L, "INGEST_TASK_READY", "key-2", Instant.now()),
              new OutboxMessagePublishedEvent(
                  3L, "INGEST_TASK_READY", "key-3", Instant.now()));

      // When
      var result = new RelayBatchResult(channel, 3, 3, 0, 0, 0, publishedEvents);

      // Then
      assertThat(result.fetched()).isEqualTo(3);
      assertThat(result.published()).isEqualTo(3);
      assertThat(result.retried()).isZero();
      assertThat(result.failed()).isZero();
      assertThat(result.leaseMissed()).isZero();
      assertThat(result.events()).hasSize(3).allMatch(e -> e instanceof OutboxMessagePublishedEvent);
    }

    @Test
    @DisplayName("应该正确表示部分失败的批次")
    void shouldRepresentPartiallyFailedBatch() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var now = Instant.now();
      var mixedEvents =
          List.<OutboxRelayDomainEvent>of(
              new OutboxMessagePublishedEvent(1L, "INGEST_TASK_READY", "key-1", now),
              new OutboxMessagePublishedEvent(2L, "INGEST_TASK_READY", "key-2", now),
              new OutboxMessageFailedEvent(
                  3L, "INGEST_TASK_READY", 3, "PUBLISH_ERROR", "Connection timeout", now),
              new OutboxLeaseMissedEvent(4L, "INGEST_TASK_READY", "relay-1", "relay-2", now));

      // When
      var result = new RelayBatchResult(channel, 4, 2, 0, 1, 1, mixedEvents);

      // Then
      assertThat(result.fetched()).isEqualTo(4);
      assertThat(result.published()).isEqualTo(2);
      assertThat(result.failed()).isEqualTo(1);
      assertThat(result.leaseMissed()).isEqualTo(1);
      assertThat(result.events()).hasSize(4);
      assertThat(result.events())
          .filteredOn(e -> e instanceof OutboxMessagePublishedEvent)
          .hasSize(2);
      assertThat(result.events())
          .filteredOn(e -> e instanceof OutboxMessageFailedEvent)
          .hasSize(1);
      assertThat(result.events())
          .filteredOn(e -> e instanceof OutboxLeaseMissedEvent)
          .hasSize(1);
    }

    @Test
    @DisplayName("应该正确表示高租约竞争场景")
    void shouldRepresentHighLeaseContentionScenario() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var now = Instant.now();
      var leaseMissedEvents =
          List.<OutboxRelayDomainEvent>of(
              new OutboxLeaseMissedEvent(1L, "INGEST_TASK_READY", "relay-1", "relay-2", now),
              new OutboxLeaseMissedEvent(2L, "INGEST_TASK_READY", "relay-1", "relay-3", now),
              new OutboxLeaseMissedEvent(3L, "INGEST_TASK_READY", "relay-1", "relay-4", now));

      // When
      var result = new RelayBatchResult(channel, 3, 0, 0, 0, 3, leaseMissedEvents);

      // Then
      assertThat(result.fetched()).isEqualTo(3);
      assertThat(result.published()).isZero();
      assertThat(result.leaseMissed()).isEqualTo(3);
      assertThat(result.events()).hasSize(3).allMatch(e -> e instanceof OutboxLeaseMissedEvent);
    }

    @Test
    @DisplayName("应该正确表示空批次（无消息可处理）")
    void shouldRepresentEmptyBatch() {
      // Given
      var channel = IngestPublishingChannels.LITERATURE_DATA_READY;

      // When
      var result = RelayBatchResult.empty(channel);

      // Then
      assertThat(result.fetched()).isZero();
      assertThat(result.published()).isZero();
      assertThat(result.retried()).isZero();
      assertThat(result.failed()).isZero();
      assertThat(result.leaseMissed()).isZero();
      assertThat(result.events()).isEmpty();
    }

    @Test
    @DisplayName("应该正确表示重试场景")
    void shouldRepresentRetryScenario() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var now = Instant.now();
      var events =
          List.<OutboxRelayDomainEvent>of(
              new OutboxMessagePublishedEvent(1L, "INGEST_TASK_READY", "key-1", now),
              new OutboxMessagePublishedEvent(2L, "INGEST_TASK_READY", "key-2", now));

      // When - 获取 5 个,发布 2 个,3 个将重试
      var result = new RelayBatchResult(channel, 5, 2, 3, 0, 0, events);

      // Then
      assertThat(result.fetched()).isEqualTo(5);
      assertThat(result.published()).isEqualTo(2);
      assertThat(result.retried()).isEqualTo(3);
      assertThat(result.failed()).isZero();
      assertThat(result.events()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("边界情况测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("应该处理最大整数计数值")
    void shouldHandleMaximumIntegerCounts() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;

      // When
      var result =
          new RelayBatchResult(
              channel,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              List.of());

      // Then
      assertThat(result.fetched()).isEqualTo(Integer.MAX_VALUE);
      assertThat(result.published()).isEqualTo(Integer.MAX_VALUE);
      assertThat(result.retried()).isEqualTo(Integer.MAX_VALUE);
      assertThat(result.failed()).isEqualTo(Integer.MAX_VALUE);
      assertThat(result.leaseMissed()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理负数计数值（虽然在业务上不合理）")
    void shouldHandleNegativeCounts() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;

      // When - Record 构造器允许负数,但业务层应该验证
      var result = new RelayBatchResult(channel, -1, -1, -1, -1, -1, List.of());

      // Then
      assertThat(result.fetched()).isEqualTo(-1);
      assertThat(result.published()).isEqualTo(-1);
      assertThat(result.retried()).isEqualTo(-1);
      assertThat(result.failed()).isEqualTo(-1);
      assertThat(result.leaseMissed()).isEqualTo(-1);
    }

    @Test
    @DisplayName("应该处理大量事件")
    void shouldHandleLargeNumberOfEvents() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var now = Instant.now();
      var largeEventList = new ArrayList<OutboxRelayDomainEvent>();
      for (long i = 1; i <= 1000; i++) {
        largeEventList.add(
            new OutboxMessagePublishedEvent(i, "INGEST_TASK_READY", "key-" + i, now));
      }

      // When
      var result = new RelayBatchResult(channel, 1000, 1000, 0, 0, 0, largeEventList);

      // Then
      assertThat(result.events()).hasSize(1000);
      assertThat(result.fetched()).isEqualTo(1000);
      assertThat(result.published()).isEqualTo(1000);
    }

    @Test
    @DisplayName("应该处理空事件列表的多种形式")
    void shouldHandleVariousFormsOfEmptyEventsList() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;

      // When & Then - null
      var result1 = new RelayBatchResult(channel, 0, 0, 0, 0, 0, null);
      assertThat(result1.events()).isEmpty();

      // When & Then - Collections.emptyList()
      var result2 = new RelayBatchResult(channel, 0, 0, 0, 0, 0, Collections.emptyList());
      assertThat(result2.events()).isEmpty();

      // When & Then - List.of()
      var result3 = new RelayBatchResult(channel, 0, 0, 0, 0, 0, List.of());
      assertThat(result3.events()).isEmpty();

      // When & Then - new ArrayList<>()
      var result4 = new RelayBatchResult(channel, 0, 0, 0, 0, 0, new ArrayList<>());
      assertThat(result4.events()).isEmpty();
    }

    @Test
    @DisplayName("应该处理单个事件的列表")
    void shouldHandleSingleEventList() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var event =
          new OutboxMessagePublishedEvent(1L, "INGEST_TASK_READY", "key-1", Instant.now());

      // When
      var result = new RelayBatchResult(channel, 1, 1, 0, 0, 0, List.of(event));

      // Then
      assertThat(result.events()).hasSize(1).containsExactly(event);
    }

    @Test
    @DisplayName("应该正确处理计数不一致的场景（业务上可能不合理但技术上允许）")
    void shouldHandleInconsistentCounts() {
      // Given
      var channel = IngestPublishingChannels.TASK_READY;
      var events =
          List.<OutboxRelayDomainEvent>of(
              new OutboxMessagePublishedEvent(
                  1L, "INGEST_TASK_READY", "key-1", Instant.now()));

      // When - fetched 为 10 但 events 只有 1 个
      var result = new RelayBatchResult(channel, 10, 8, 1, 1, 2, events);

      // Then - Record 不验证业务逻辑一致性,仅存储值
      assertThat(result.fetched()).isEqualTo(10);
      assertThat(result.events()).hasSize(1);
    }
  }

  // === 测试辅助方法 ===

  /** 创建示例事件列表用于测试。 */
  private static List<OutboxRelayDomainEvent> createSampleEvents() {
    var now = Instant.parse("2025-01-15T10:00:00Z");
    return List.of(
        new OutboxMessagePublishedEvent(1L, "INGEST_TASK_READY", "partition-1", now),
        new OutboxMessageFailedEvent(
            2L, "INGEST_TASK_READY", 3, "PUBLISH_ERROR", "Failed to publish message", now),
        new OutboxLeaseMissedEvent(3L, "INGEST_TASK_READY", "relay-1", "relay-2", now));
  }
}
