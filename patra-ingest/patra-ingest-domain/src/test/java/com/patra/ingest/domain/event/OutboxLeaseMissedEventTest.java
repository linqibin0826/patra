package com.patra.ingest.domain.event;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * OutboxLeaseMissedEvent 单元测试。
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
 *   <li>✅ 业务场景测试（租约冲突）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("OutboxLeaseMissedEvent 单元测试")
class OutboxLeaseMissedEventTest {

  // ========== Record 构造器测试 ==========

  @Nested
  @DisplayName("Record 构造器")
  class RecordConstructorTests {

    @Test
    @DisplayName("应该成功创建租约竞争失败事件")
    void shouldCreateLeaseMissedEvent() {
      // Given
      Long messageId = 1001L;
      String channel = "literature.parsed";
      String requestedLeaseOwner = "relay-instance-01";
      String currentLeaseOwner = "relay-instance-02";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              messageId, channel, requestedLeaseOwner, currentLeaseOwner, occurredAt);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.messageId()).isEqualTo(messageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.requestedLeaseOwner()).isEqualTo(requestedLeaseOwner);
      assertThat(event.currentLeaseOwner()).isEqualTo(currentLeaseOwner);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("应该允许 messageId 为 null（异常情况）")
    void shouldAllowNullMessageId() {
      // Given
      Long messageId = null;

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              messageId,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
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
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, channel, "relay-instance-01", "relay-instance-02", Instant.now());

      // Then
      assertThat(event.channel()).isNull();
    }

    @Test
    @DisplayName("应该允许 requestedLeaseOwner 为 null（异常情况）")
    void shouldAllowNullRequestedLeaseOwner() {
      // Given
      String requestedLeaseOwner = null;

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", requestedLeaseOwner, "relay-instance-02", Instant.now());

      // Then
      assertThat(event.requestedLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("应该允许 currentLeaseOwner 为 null（异常情况）")
    void shouldAllowNullCurrentLeaseOwner() {
      // Given
      String currentLeaseOwner = null;

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", currentLeaseOwner, Instant.now());

      // Then
      assertThat(event.currentLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("应该允许 occurredAt 为 null（异常情况）")
    void shouldAllowNullOccurredAt() {
      // Given
      Instant occurredAt = null;

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", occurredAt);

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
      String channel = "literature.parsed";
      String requestedLeaseOwner = "relay-instance-01";
      String currentLeaseOwner = "relay-instance-02";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              messageId, channel, requestedLeaseOwner, currentLeaseOwner, occurredAt);
      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              messageId, channel, requestedLeaseOwner, currentLeaseOwner, occurredAt);

      // Then
      assertThat(event1).isEqualTo(event2);
      assertThat(event1.equals(event2)).isTrue();
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 messageId 的事件应该不相等")
    void shouldImplementEquals_DifferentMessageId() {
      // Given
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.now());
      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1002L, // 不同的 messageId
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.now());

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 channel 的事件应该不相等")
    void shouldImplementEquals_DifferentChannel() {
      // Given
      Instant now = Instant.now();
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", now);
      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1001L, "literature.ready", "relay-instance-01", "relay-instance-02", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 requestedLeaseOwner 的事件应该不相等")
    void shouldImplementEquals_DifferentRequestedLeaseOwner() {
      // Given
      Instant now = Instant.now();
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", now);
      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-03", "relay-instance-02", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 currentLeaseOwner 的事件应该不相等")
    void shouldImplementEquals_DifferentCurrentLeaseOwner() {
      // Given
      Instant now = Instant.now();
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", now);
      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-03", now);

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同 occurredAt 的事件应该不相等")
    void shouldImplementEquals_DifferentOccurredAt() {
      // Given
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.parse("2024-01-15T10:30:00Z"));
      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.parse("2024-01-15T10:30:01Z"));

      // Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 相同字段值的事件应该有相同的 hashCode")
    void shouldImplementHashCode_SameFieldValues() {
      // Given
      Long messageId = 1001L;
      String channel = "literature.parsed";
      String requestedLeaseOwner = "relay-instance-01";
      String currentLeaseOwner = "relay-instance-02";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              messageId, channel, requestedLeaseOwner, currentLeaseOwner, occurredAt);
      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              messageId, channel, requestedLeaseOwner, currentLeaseOwner, occurredAt);

      // Then
      assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 不同字段值的事件可能有不同的 hashCode")
    void shouldImplementHashCode_DifferentFieldValues() {
      // Given
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.now());
      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1002L,
              "literature.ready",
              "relay-instance-03",
              "relay-instance-04",
              Instant.now());

      // Then - 注意：不同的对象可能有相同的 hashCode（冲突），但通常应该不同
      assertThat(event1.hashCode()).isNotEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString - 包含所有字段信息")
    void shouldImplementToString_ContainsAllFields() {
      // Given
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.parse("2024-01-15T10:30:00Z"));

      // When
      String toString = event.toString();

      // Then
      assertThat(toString).contains("OutboxLeaseMissedEvent");
      assertThat(toString).contains("messageId=1001");
      assertThat(toString).contains("channel=literature.parsed");
      assertThat(toString).contains("requestedLeaseOwner=relay-instance-01");
      assertThat(toString).contains("currentLeaseOwner=relay-instance-02");
      assertThat(toString).contains("occurredAt=2024-01-15T10:30:00Z");
    }

    @Test
    @DisplayName("应该正确实现 toString - 处理 null 字段")
    void shouldImplementToString_HandlesNullFields() {
      // Given
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(null, null, null, null, null);

      // When
      String toString = event.toString();

      // Then
      assertThat(toString).contains("OutboxLeaseMissedEvent");
      assertThat(toString).contains("messageId=null");
      assertThat(toString).contains("channel=null");
      assertThat(toString).contains("requestedLeaseOwner=null");
      assertThat(toString).contains("currentLeaseOwner=null");
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
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              messageId,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.now());

      // When & Then
      assertThat(event.messageId()).isEqualTo(messageId);
    }

    @Test
    @DisplayName("应该正确返回 channel")
    void shouldReturnChannel() {
      // Given
      String channel = "literature.parsed";
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, channel, "relay-instance-01", "relay-instance-02", Instant.now());

      // When & Then
      assertThat(event.channel()).isEqualTo(channel);
    }

    @Test
    @DisplayName("应该正确返回 requestedLeaseOwner")
    void shouldReturnRequestedLeaseOwner() {
      // Given
      String requestedLeaseOwner = "relay-instance-01";
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", requestedLeaseOwner, "relay-instance-02", Instant.now());

      // When & Then
      assertThat(event.requestedLeaseOwner()).isEqualTo(requestedLeaseOwner);
    }

    @Test
    @DisplayName("应该正确返回 currentLeaseOwner")
    void shouldReturnCurrentLeaseOwner() {
      // Given
      String currentLeaseOwner = "relay-instance-02";
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", currentLeaseOwner, Instant.now());

      // When & Then
      assertThat(event.currentLeaseOwner()).isEqualTo(currentLeaseOwner);
    }

    @Test
    @DisplayName("应该正确返回 occurredAt")
    void shouldReturnOccurredAt() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", occurredAt);

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
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.now());

      // When & Then
      assertThat(event).isInstanceOf(OutboxRelayDomainEvent.class);
    }

    @Test
    @DisplayName("应该实现 DomainEvent 接口")
    void shouldImplementDomainEventInterface() {
      // Given
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.now());

      // When & Then
      assertThat(event).isInstanceOf(com.patra.common.domain.DomainEvent.class);
    }

    @Test
    @DisplayName("应该正确实现 occurredAt 方法（DomainEvent 接口要求）")
    void shouldImplementOccurredAtMethod() {
      // Given
      Instant expectedOccurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
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
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
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
      String channel = "literature.parsed";
      String requestedLeaseOwner = "relay-instance-01";
      String currentLeaseOwner = "relay-instance-02";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              messageId, channel, requestedLeaseOwner, currentLeaseOwner, occurredAt);

      // When - 保存初始值
      Long initialMessageId = event.messageId();
      String initialChannel = event.channel();
      String initialRequestedLeaseOwner = event.requestedLeaseOwner();
      String initialCurrentLeaseOwner = event.currentLeaseOwner();
      Instant initialOccurredAt = event.occurredAt();

      // Then - 字段应该保持不变（Record 自动为 final，无 setter 方法）
      assertThat(event.messageId()).isEqualTo(initialMessageId);
      assertThat(event.channel()).isEqualTo(initialChannel);
      assertThat(event.requestedLeaseOwner()).isEqualTo(initialRequestedLeaseOwner);
      assertThat(event.currentLeaseOwner()).isEqualTo(initialCurrentLeaseOwner);
      assertThat(event.occurredAt()).isEqualTo(initialOccurredAt);
    }

    @Test
    @DisplayName("应该确保 Instant 字段不可变")
    void shouldEnsureInstantFieldIsImmutable() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", occurredAt);

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
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              largeMessageId,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
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
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              negativeMessageId,
              "literature.parsed",
              "relay-instance-01",
              "relay-instance-02",
              Instant.now());

      // Then
      assertThat(event.messageId()).isEqualTo(negativeMessageId);
    }

    @Test
    @DisplayName("应该处理极长的 channel 名称")
    void shouldHandleVeryLongChannelName() {
      // Given
      String longChannel = "a".repeat(1000);

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, longChannel, "relay-instance-01", "relay-instance-02", Instant.now());

      // Then
      assertThat(event.channel()).hasSize(1000);
      assertThat(event.channel()).isEqualTo(longChannel);
    }

    @Test
    @DisplayName("应该处理极长的租约所有者名称")
    void shouldHandleVeryLongLeaseOwnerNames() {
      // Given
      String longRequestedOwner = "requested-".concat("a".repeat(500));
      String longCurrentOwner = "current-".concat("b".repeat(500));

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", longRequestedOwner, longCurrentOwner, Instant.now());

      // Then
      assertThat(event.requestedLeaseOwner()).contains("requested-");
      assertThat(event.requestedLeaseOwner()).hasSize(510);
      assertThat(event.currentLeaseOwner()).contains("current-");
      assertThat(event.currentLeaseOwner()).hasSize(508);
    }

    @Test
    @DisplayName("应该处理极端时间边界（Unix Epoch）")
    void shouldHandleEpochTime() {
      // Given
      Instant epoch = Instant.parse("1970-01-01T00:00:00Z");

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", epoch);

      // Then
      assertThat(event.occurredAt()).isEqualTo(epoch);
    }

    @Test
    @DisplayName("应该处理远期时间")
    void shouldHandleFutureTime() {
      // Given
      Instant future = Instant.parse("2099-12-31T23:59:59Z");

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", future);

      // Then
      assertThat(event.occurredAt()).isEqualTo(future);
    }

    @Test
    @DisplayName("应该处理纳秒级时间精度")
    void shouldHandleNanosecondTimePrecision() {
      // Given
      Instant preciseTime = Instant.parse("2024-01-15T10:30:45.123456789Z");

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", preciseTime);

      // Then
      assertThat(event.occurredAt()).isEqualTo(preciseTime);
      assertThat(event.occurredAt().getNano()).isEqualTo(123456789);
    }

    @Test
    @DisplayName("应该处理空字符串字段")
    void shouldHandleEmptyStringFields() {
      // Given
      String emptyChannel = "";
      String emptyRequestedOwner = "";
      String emptyCurrentOwner = "";

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, emptyChannel, emptyRequestedOwner, emptyCurrentOwner, Instant.now());

      // Then
      assertThat(event.channel()).isEmpty();
      assertThat(event.requestedLeaseOwner()).isEmpty();
      assertThat(event.currentLeaseOwner()).isEmpty();
    }

    @Test
    @DisplayName("应该处理包含特殊字符的字段")
    void shouldHandleSpecialCharactersInFields() {
      // Given
      String channelWithSpecialChars = "literature.parsed:v1.0-beta@2024";
      String ownerWithSpecialChars = "relay-instance-01_pod-abc123!@#$%";

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L,
              channelWithSpecialChars,
              ownerWithSpecialChars,
              "relay-instance-02",
              Instant.now());

      // Then
      assertThat(event.channel()).isEqualTo(channelWithSpecialChars);
      assertThat(event.requestedLeaseOwner()).isEqualTo(ownerWithSpecialChars);
    }

    @Test
    @DisplayName("应该处理相同的 requestedLeaseOwner 和 currentLeaseOwner")
    void shouldHandleSameLeaseOwners() {
      // Given
      String sameOwner = "relay-instance-01";

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", sameOwner, sameOwner, Instant.now());

      // Then
      assertThat(event.requestedLeaseOwner()).isEqualTo(event.currentLeaseOwner());
      assertThat(event.requestedLeaseOwner()).isEqualTo(sameOwner);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenarioTests {

    @Test
    @DisplayName("应该正确记录租约竞争失败场景")
    void shouldRecordLeaseCompetitionFailureScenario() {
      // Given - 实例 01 尝试获取租约，但实例 02 已经持有
      Long hotspotMessageId = 1001L;
      String channel = "literature.parsed";
      String requestedLeaseOwner = "relay-instance-01";
      String currentLeaseOwner = "relay-instance-02";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      OutboxLeaseMissedEvent event =
          new OutboxLeaseMissedEvent(
              hotspotMessageId, channel, requestedLeaseOwner, currentLeaseOwner, occurredAt);

      // Then - 应该完整记录竞争失败信息
      assertThat(event.messageId()).isEqualTo(hotspotMessageId);
      assertThat(event.channel()).isEqualTo(channel);
      assertThat(event.requestedLeaseOwner()).isEqualTo(requestedLeaseOwner);
      assertThat(event.currentLeaseOwner()).isEqualTo(currentLeaseOwner);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
      assertThat(event.requestedLeaseOwner()).isNotEqualTo(event.currentLeaseOwner());
    }

    @Test
    @DisplayName("应该支持热点消息分析场景（同一消息多次竞争失败）")
    void shouldSupportHotspotMessageAnalysisScenario() {
      // Given - 同一消息多次被竞争
      Long hotspotMessageId = 1001L;
      String channel = "literature.parsed";

      // When - 模拟 3 次不同实例的竞争失败
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              hotspotMessageId,
              channel,
              "relay-instance-01",
              "relay-instance-02",
              Instant.parse("2024-01-15T10:30:00Z"));

      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              hotspotMessageId,
              channel,
              "relay-instance-03",
              "relay-instance-02",
              Instant.parse("2024-01-15T10:30:01Z"));

      OutboxLeaseMissedEvent event3 =
          new OutboxLeaseMissedEvent(
              hotspotMessageId,
              channel,
              "relay-instance-04",
              "relay-instance-02",
              Instant.parse("2024-01-15T10:30:02Z"));

      // Then - 应该可以按 channel + messageId 聚合分析热点
      assertThat(event1.messageId()).isEqualTo(hotspotMessageId);
      assertThat(event2.messageId()).isEqualTo(hotspotMessageId);
      assertThat(event3.messageId()).isEqualTo(hotspotMessageId);
      assertThat(event1.channel()).isEqualTo(channel);
      assertThat(event2.channel()).isEqualTo(channel);
      assertThat(event3.channel()).isEqualTo(channel);

      // 同一消息被不同实例竞争
      assertThat(event1.requestedLeaseOwner()).isNotEqualTo(event2.requestedLeaseOwner());
      assertThat(event2.requestedLeaseOwner()).isNotEqualTo(event3.requestedLeaseOwner());
    }

    @Test
    @DisplayName("应该支持扩展决策场景（高并发度检测）")
    void shouldSupportScalingDecisionScenario() {
      // Given - 多个实例频繁竞争失败，可能表明并发度过高
      String channel = "literature.parsed";
      Instant baseTime = Instant.parse("2024-01-15T10:30:00Z");

      // When - 模拟 5 次短时间内的竞争失败
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L, channel, "relay-instance-01", "relay-instance-02", baseTime);

      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1002L, channel, "relay-instance-03", "relay-instance-04", baseTime.plusSeconds(1));

      OutboxLeaseMissedEvent event3 =
          new OutboxLeaseMissedEvent(
              1003L, channel, "relay-instance-05", "relay-instance-06", baseTime.plusSeconds(2));

      OutboxLeaseMissedEvent event4 =
          new OutboxLeaseMissedEvent(
              1004L, channel, "relay-instance-07", "relay-instance-08", baseTime.plusSeconds(3));

      OutboxLeaseMissedEvent event5 =
          new OutboxLeaseMissedEvent(
              1005L, channel, "relay-instance-09", "relay-instance-10", baseTime.plusSeconds(4));

      // Then - 应该可以检测到高频竞争失败
      assertThat(event1.channel()).isEqualTo(channel);
      assertThat(event2.channel()).isEqualTo(channel);
      assertThat(event3.channel()).isEqualTo(channel);
      assertThat(event4.channel()).isEqualTo(channel);
      assertThat(event5.channel()).isEqualTo(channel);

      // 时间窗口内多次竞争失败
      assertThat(event5.occurredAt().getEpochSecond() - event1.occurredAt().getEpochSecond())
          .isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("应该支持租约所有者追踪场景")
    void shouldSupportLeaseOwnerTrackingScenario() {
      // Given - 追踪哪个实例持有租约
      String dominantOwner = "relay-instance-02";

      // When - 多次竞争失败，都是同一个实例持有租约
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L,
              "literature.parsed",
              "relay-instance-01",
              dominantOwner,
              Instant.now());

      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1002L,
              "literature.parsed",
              "relay-instance-03",
              dominantOwner,
              Instant.now());

      OutboxLeaseMissedEvent event3 =
          new OutboxLeaseMissedEvent(
              1003L,
              "literature.parsed",
              "relay-instance-04",
              dominantOwner,
              Instant.now());

      // Then - 应该可以识别出支配性的租约持有者
      assertThat(event1.currentLeaseOwner()).isEqualTo(dominantOwner);
      assertThat(event2.currentLeaseOwner()).isEqualTo(dominantOwner);
      assertThat(event3.currentLeaseOwner()).isEqualTo(dominantOwner);
    }

    @Test
    @DisplayName("应该支持按通道分析场景")
    void shouldSupportChannelBasedAnalysisScenario() {
      // Given - 不同通道的竞争失败情况
      String highContentionChannel = "literature.parsed";
      String lowContentionChannel = "provenance.changed";

      // When
      OutboxLeaseMissedEvent highContentionEvent =
          new OutboxLeaseMissedEvent(
              1001L,
              highContentionChannel,
              "relay-instance-01",
              "relay-instance-02",
              Instant.now());

      OutboxLeaseMissedEvent lowContentionEvent =
          new OutboxLeaseMissedEvent(
              2001L,
              lowContentionChannel,
              "relay-instance-03",
              "relay-instance-04",
              Instant.now());

      // Then - 应该可以按通道区分竞争水平
      assertThat(highContentionEvent.channel()).isEqualTo(highContentionChannel);
      assertThat(lowContentionEvent.channel()).isEqualTo(lowContentionChannel);
      assertThat(highContentionEvent.channel()).isNotEqualTo(lowContentionEvent.channel());
    }

    @Test
    @DisplayName("应该支持时间序列分析场景")
    void shouldSupportTimeSeriesAnalysisScenario() {
      // Given - 按时间序列记录竞争失败
      Instant t1 = Instant.parse("2024-01-15T10:00:00Z");
      Instant t2 = Instant.parse("2024-01-15T11:00:00Z");
      Instant t3 = Instant.parse("2024-01-15T12:00:00Z");

      // When
      OutboxLeaseMissedEvent event1 =
          new OutboxLeaseMissedEvent(
              1001L, "literature.parsed", "relay-instance-01", "relay-instance-02", t1);

      OutboxLeaseMissedEvent event2 =
          new OutboxLeaseMissedEvent(
              1002L, "literature.parsed", "relay-instance-03", "relay-instance-04", t2);

      OutboxLeaseMissedEvent event3 =
          new OutboxLeaseMissedEvent(
              1003L, "literature.parsed", "relay-instance-05", "relay-instance-06", t3);

      // Then - 应该可以按时间排序分析竞争趋势
      assertThat(event1.occurredAt()).isBefore(event2.occurredAt());
      assertThat(event2.occurredAt()).isBefore(event3.occurredAt());
      assertThat(event1.occurredAt()).isEqualTo(t1);
      assertThat(event2.occurredAt()).isEqualTo(t2);
      assertThat(event3.occurredAt()).isEqualTo(t3);
    }
  }
}
