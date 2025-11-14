package com.patra.ingest.domain.event;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SliceStatusChangedEvent 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>不使用 Mockito，使用真实对象
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 *   <li>测试 Record 语义（构造器、访问器、equals/hashCode/toString）
 *   <li>测试 DomainEvent 接口实现
 * </ul>
 *
 * <p>测试范围：
 *
 * <ul>
 *   <li>✅ Record 构造器测试（紧凑构造器逻辑）
 *   <li>✅ 工厂方法测试（of()）
 *   <li>✅ 访问器方法测试（sliceId, planId, oldStatus, newStatus, occurredAt）
 *   <li>✅ DomainEvent 接口实现测试
 *   <li>✅ 时间戳自动填充测试
 *   <li>✅ Record 语义测试（equals, hashCode, toString）
 *   <li>✅ 幂等性键测试（sliceId + newStatus）
 *   <li>✅ 状态转换场景测试
 *   <li>✅ 边界情况测试
 *   <li>✅ 不可变性测试
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("SliceStatusChangedEvent 单元测试")
class SliceStatusChangedEventTest {

  // ========== Record 构造器测试 ==========

  @Nested
  @DisplayName("Record 构造器")
  class RecordConstructorTests {

    @Test
    @DisplayName("应该通过标准构造器成功创建事件")
    void shouldCreateEventViaStandardConstructor() {
      // Given
      Long sliceId = 1001L;
      Long planId = 2001L;
      String oldStatus = "EXECUTING";
      String newStatus = "SUCCEEDED";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(sliceId, planId, oldStatus, newStatus, occurredAt);

      // Then
      assertThat(event.sliceId()).isEqualTo(sliceId);
      assertThat(event.planId()).isEqualTo(planId);
      assertThat(event.oldStatus()).isEqualTo(oldStatus);
      assertThat(event.newStatus()).isEqualTo(newStatus);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("应该自动填充 occurredAt 当传入 null")
    void shouldAutoFillOccurredAtWhenNull() {
      // Given
      Instant before = Instant.now();

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", null);

      // Then
      Instant after = Instant.now();
      assertThat(event.occurredAt()).isNotNull().isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("应该保留非 null 的 occurredAt")
    void shouldPreserveNonNullOccurredAt() {
      // Given
      Instant specificTime = Instant.parse("2024-01-15T10:30:00Z");

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", specificTime);

      // Then
      assertThat(event.occurredAt()).isEqualTo(specificTime);
    }

    @Test
    @DisplayName("应该处理 null 的 oldStatus（首次创建场景）")
    void shouldHandleNullOldStatus() {
      // Given
      String oldStatus = null; // 首次创建，没有旧状态

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, oldStatus, "EXECUTING", Instant.now());

      // Then
      assertThat(event.oldStatus()).isNull();
      assertThat(event.newStatus()).isEqualTo("EXECUTING");
    }
  }

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("of() 工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("应该通过工厂方法成功创建事件并自动填充时间戳")
    void shouldCreateEventViaFactoryMethodWithAutoTimestamp() {
      // Given
      Long sliceId = 1001L;
      Long planId = 2001L;
      String oldStatus = "EXECUTING";
      String newStatus = "SUCCEEDED";
      Instant before = Instant.now();

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(sliceId, planId, oldStatus, newStatus);

      // Then
      Instant after = Instant.now();
      assertThat(event.sliceId()).isEqualTo(sliceId);
      assertThat(event.planId()).isEqualTo(planId);
      assertThat(event.oldStatus()).isEqualTo(oldStatus);
      assertThat(event.newStatus()).isEqualTo(newStatus);
      assertThat(event.occurredAt()).isNotNull().isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("应该为不同事件生成不同的时间戳")
    void shouldGenerateDifferentTimestampsForDifferentEvents() throws InterruptedException {
      // Given & When
      SliceStatusChangedEvent event1 =
          SliceStatusChangedEvent.of(1001L, 2001L, "PENDING", "EXECUTING");
      Thread.sleep(10); // 确保时间戳不同
      SliceStatusChangedEvent event2 =
          SliceStatusChangedEvent.of(1002L, 2001L, "EXECUTING", "SUCCEEDED");

      // Then
      assertThat(event1.occurredAt()).isNotEqualTo(event2.occurredAt());
      assertThat(event1.occurredAt()).isBefore(event2.occurredAt());
    }

    @Test
    @DisplayName("应该支持通过工厂方法创建首次状态变更事件（oldStatus 为 null）")
    void shouldSupportCreatingFirstStatusChangeEventViaFactoryMethod() {
      // Given
      String oldStatus = null;

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, oldStatus, "EXECUTING");

      // Then
      assertThat(event.oldStatus()).isNull();
      assertThat(event.newStatus()).isEqualTo("EXECUTING");
      assertThat(event.occurredAt()).isNotNull();
    }
  }

  // ========== 访问器方法测试 ==========

  @Nested
  @DisplayName("访问器方法")
  class AccessorMethodTests {

    @Test
    @DisplayName("应该正确返回所有字段值")
    void shouldReturnAllFieldValues() {
      // Given
      Long sliceId = 1001L;
      Long planId = 2001L;
      String oldStatus = "EXECUTING";
      String newStatus = "SUCCEEDED";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(sliceId, planId, oldStatus, newStatus, occurredAt);

      // Then
      assertThat(event.sliceId()).isEqualTo(sliceId);
      assertThat(event.planId()).isEqualTo(planId);
      assertThat(event.oldStatus()).isEqualTo(oldStatus);
      assertThat(event.newStatus()).isEqualTo(newStatus);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("应该支持链式访问")
    void shouldSupportChainedAccess() {
      // Given
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, "EXECUTING", "SUCCEEDED");

      // When & Then
      assertThat(event.sliceId()).isNotNull();
      assertThat(event.planId()).isNotNull();
      assertThat(event.newStatus()).isNotBlank();
      assertThat(event.occurredAt()).isNotNull();
    }
  }

  // ========== DomainEvent 接口实现测试 ==========

  @Nested
  @DisplayName("DomainEvent 接口实现")
  class DomainEventInterfaceTests {

    @Test
    @DisplayName("应该实现 DomainEvent 接口")
    void shouldImplementDomainEventInterface() {
      // Given
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, "EXECUTING", "SUCCEEDED");

      // When & Then
      assertThat(event).isInstanceOf(com.patra.common.domain.DomainEvent.class);
    }

    @Test
    @DisplayName("应该通过 occurredAt() 方法返回事件发生时间")
    void shouldReturnEventOccurrenceTimeViaOccurredAtMethod() {
      // Given
      Instant specificTime = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", specificTime);

      // When
      Instant eventTime = event.occurredAt();

      // Then
      assertThat(eventTime).isEqualTo(specificTime);
    }

    @Test
    @DisplayName("应该支持序列化（实现 Serializable）")
    void shouldSupportSerialization() {
      // Given
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, "EXECUTING", "SUCCEEDED");

      // When & Then
      assertThat(event).isInstanceOf(java.io.Serializable.class);
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义（equals/hashCode/toString）")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该基于所有字段实现 equals")
    void shouldImplementEqualsBasedOnAllFields() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);

      // When & Then
      assertThat(event1).isEqualTo(event2);
    }

    @Test
    @DisplayName("应该在 sliceId 不同时返回不相等")
    void shouldReturnNotEqualWhenSliceIdDiffers() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(1002L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该在 planId 不同时返回不相等")
    void shouldReturnNotEqualWhenPlanIdDiffers() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(1001L, 2002L, "EXECUTING", "SUCCEEDED", occurredAt);

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该在 oldStatus 不同时返回不相等")
    void shouldReturnNotEqualWhenOldStatusDiffers() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(1001L, 2001L, "PENDING", "SUCCEEDED", occurredAt);

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该在 newStatus 不同时返回不相等")
    void shouldReturnNotEqualWhenNewStatusDiffers() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "FAILED", occurredAt);

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该在 occurredAt 不同时返回不相等")
    void shouldReturnNotEqualWhenOccurredAtDiffers() {
      // Given
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(
              1001L, 2001L, "EXECUTING", "SUCCEEDED", Instant.parse("2024-01-15T10:30:00Z"));
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(
              1001L, 2001L, "EXECUTING", "SUCCEEDED", Instant.parse("2024-01-15T10:30:01Z"));

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("应该为相同字段的对象生成相同的 hashCode")
    void shouldGenerateSameHashCodeForSameFields() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);

      // When & Then
      assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("应该为不同字段的对象生成不同的 hashCode（高概率）")
    void shouldGenerateDifferentHashCodeForDifferentFields() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(1002L, 2001L, "EXECUTING", "SUCCEEDED", occurredAt);

      // When & Then
      assertThat(event1.hashCode()).isNotEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("应该生成包含所有字段的 toString")
    void shouldGenerateToStringWithAllFields() {
      // Given
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(
              1001L, 2001L, "EXECUTING", "SUCCEEDED", Instant.parse("2024-01-15T10:30:00Z"));

      // When
      String toString = event.toString();

      // Then
      assertThat(toString)
          .contains("SliceStatusChangedEvent")
          .contains("sliceId=1001")
          .contains("planId=2001")
          .contains("oldStatus=EXECUTING")
          .contains("newStatus=SUCCEEDED")
          .contains("occurredAt=2024-01-15T10:30:00Z");
    }

    @Test
    @DisplayName("应该在 toString 中正确处理 null 字段")
    void shouldHandleNullFieldsInToString() {
      // Given
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, null, "EXECUTING", Instant.now());

      // When
      String toString = event.toString();

      // Then
      assertThat(toString).contains("oldStatus=null");
    }
  }

  // ========== 幂等性键测试 ==========

  @Nested
  @DisplayName("幂等性键测试（sliceId + newStatus）")
  class IdempotencyKeyTests {

    @Test
    @DisplayName("应该使用 sliceId + newStatus 作为幂等性复合键")
    void shouldUseSliceIdAndNewStatusAsIdempotencyKey() {
      // Given
      Long sliceId = 1001L;
      String newStatus = "SUCCEEDED";

      // When
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(sliceId, 2001L, "EXECUTING", newStatus, Instant.now());
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(sliceId, 2001L, "PENDING", newStatus, Instant.now());

      // Then - 相同的 sliceId + newStatus 应该被视为相同的状态变更
      assertThat(event1.sliceId()).isEqualTo(event2.sliceId());
      assertThat(event1.newStatus()).isEqualTo(event2.newStatus());
    }

    @Test
    @DisplayName("应该区分不同 sliceId 的相同状态变更")
    void shouldDistinguishSameStatusChangeForDifferentSlices() {
      // Given
      String newStatus = "SUCCEEDED";

      // When
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", newStatus, Instant.now());
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(1002L, 2001L, "EXECUTING", newStatus, Instant.now());

      // Then - 不同的 sliceId 应该被视为不同的事件
      assertThat(event1.sliceId()).isNotEqualTo(event2.sliceId());
    }

    @Test
    @DisplayName("应该区分相同 sliceId 的不同状态变更")
    void shouldDistinguishDifferentStatusChangesForSameSlice() {
      // Given
      Long sliceId = 1001L;

      // When
      SliceStatusChangedEvent event1 =
          new SliceStatusChangedEvent(sliceId, 2001L, "EXECUTING", "SUCCEEDED", Instant.now());
      SliceStatusChangedEvent event2 =
          new SliceStatusChangedEvent(sliceId, 2001L, "EXECUTING", "FAILED", Instant.now());

      // Then - 不同的 newStatus 应该被视为不同的事件
      assertThat(event1.newStatus()).isNotEqualTo(event2.newStatus());
    }
  }

  // ========== 状态转换场景测试 ==========

  @Nested
  @DisplayName("状态转换场景")
  class StatusTransitionScenarioTests {

    @Test
    @DisplayName("应该支持从 null 到 EXECUTING 的首次状态转换")
    void shouldSupportFirstTransitionFromNullToExecuting() {
      // Given
      String oldStatus = null;
      String newStatus = "EXECUTING";

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, oldStatus, newStatus);

      // Then
      assertThat(event.oldStatus()).isNull();
      assertThat(event.newStatus()).isEqualTo("EXECUTING");
    }

    @Test
    @DisplayName("应该支持从 EXECUTING 到 SUCCEEDED 的成功转换")
    void shouldSupportSuccessfulTransitionFromExecutingToSucceeded() {
      // Given
      String oldStatus = "EXECUTING";
      String newStatus = "SUCCEEDED";

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, oldStatus, newStatus);

      // Then
      assertThat(event.oldStatus()).isEqualTo("EXECUTING");
      assertThat(event.newStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("应该支持从 EXECUTING 到 FAILED 的失败转换")
    void shouldSupportFailedTransitionFromExecutingToFailed() {
      // Given
      String oldStatus = "EXECUTING";
      String newStatus = "FAILED";

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, oldStatus, newStatus);

      // Then
      assertThat(event.oldStatus()).isEqualTo("EXECUTING");
      assertThat(event.newStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("应该支持从 EXECUTING 到 PARTIAL 的部分完成转换")
    void shouldSupportPartialTransitionFromExecutingToPartial() {
      // Given
      String oldStatus = "EXECUTING";
      String newStatus = "PARTIAL";

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, oldStatus, newStatus);

      // Then
      assertThat(event.oldStatus()).isEqualTo("EXECUTING");
      assertThat(event.newStatus()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("应该支持相同状态的转换（幂等性重试场景）")
    void shouldSupportSameStatusTransitionForIdempotency() {
      // Given
      String oldStatus = "SUCCEEDED";
      String newStatus = "SUCCEEDED";

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, oldStatus, newStatus);

      // Then - 业务上可能是幂等性重试，应该允许
      assertThat(event.oldStatus()).isEqualTo("SUCCEEDED");
      assertThat(event.newStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("应该正确记录相同 Plan 下的多个 Slice 状态变更")
    void shouldRecordMultipleSliceStatusChangesUnderSamePlan() {
      // Given
      Long planId = 2001L;

      // When
      SliceStatusChangedEvent slice1Event =
          SliceStatusChangedEvent.of(1001L, planId, "EXECUTING", "SUCCEEDED");
      SliceStatusChangedEvent slice2Event =
          SliceStatusChangedEvent.of(1002L, planId, "EXECUTING", "FAILED");
      SliceStatusChangedEvent slice3Event =
          SliceStatusChangedEvent.of(1003L, planId, "EXECUTING", "PARTIAL");

      // Then - 相同 planId 下的多个 Slice 状态变更应该被正确记录
      assertThat(slice1Event.planId()).isEqualTo(planId);
      assertThat(slice2Event.planId()).isEqualTo(planId);
      assertThat(slice3Event.planId()).isEqualTo(planId);
      assertThat(slice1Event.newStatus()).isEqualTo("SUCCEEDED");
      assertThat(slice2Event.newStatus()).isEqualTo("FAILED");
      assertThat(slice3Event.newStatus()).isEqualTo("PARTIAL");
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极小的 ID 值")
    void shouldHandleMinimumIdValues() {
      // Given
      Long sliceId = 1L;
      Long planId = 1L;

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(sliceId, planId, "EXECUTING", "SUCCEEDED");

      // Then
      assertThat(event.sliceId()).isEqualTo(1L);
      assertThat(event.planId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该处理极大的 ID 值")
    void shouldHandleMaximumIdValues() {
      // Given
      Long sliceId = Long.MAX_VALUE;
      Long planId = Long.MAX_VALUE;

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(sliceId, planId, "EXECUTING", "SUCCEEDED");

      // Then
      assertThat(event.sliceId()).isEqualTo(Long.MAX_VALUE);
      assertThat(event.planId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理极长的状态字符串")
    void shouldHandleVeryLongStatusStrings() {
      // Given
      String longStatus = "STATUS_".repeat(100); // 700 字符

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, longStatus, longStatus);

      // Then
      assertThat(event.oldStatus()).hasSize(700);
      assertThat(event.newStatus()).hasSize(700);
    }

    @Test
    @DisplayName("应该处理空字符串状态")
    void shouldHandleEmptyStatusStrings() {
      // Given
      String emptyStatus = "";

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, emptyStatus, emptyStatus);

      // Then
      assertThat(event.oldStatus()).isEmpty();
      assertThat(event.newStatus()).isEmpty();
    }

    @Test
    @DisplayName("应该处理包含特殊字符的状态")
    void shouldHandleStatusWithSpecialCharacters() {
      // Given
      String specialStatus = "FAILED:IO_ERROR:java.io.IOException";

      // When
      SliceStatusChangedEvent event =
          SliceStatusChangedEvent.of(1001L, 2001L, "EXECUTING", specialStatus);

      // Then
      assertThat(event.newStatus()).isEqualTo(specialStatus);
      assertThat(event.newStatus()).contains(":");
    }

    @Test
    @DisplayName("应该处理极端时间边界（Unix Epoch）")
    void shouldHandleEpochTime() {
      // Given
      Instant epoch = Instant.parse("1970-01-01T00:00:00Z");

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", epoch);

      // Then
      assertThat(event.occurredAt()).isEqualTo(epoch);
    }

    @Test
    @DisplayName("应该处理远期时间")
    void shouldHandleFutureTime() {
      // Given
      Instant future = Instant.parse("2099-12-31T23:59:59Z");

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", future);

      // Then
      assertThat(event.occurredAt()).isEqualTo(future);
    }

    @Test
    @DisplayName("应该处理纳秒级时间精度")
    void shouldHandleNanosecondTimePrecision() {
      // Given
      Instant preciseTime = Instant.parse("2024-01-15T10:30:45.123456789Z");

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", preciseTime);

      // Then
      assertThat(event.occurredAt()).isEqualTo(preciseTime);
      assertThat(event.occurredAt().getNano()).isEqualTo(123456789);
    }
  }

  // ========== 不可变性测试 ==========

  @Nested
  @DisplayName("不可变性")
  class ImmutabilityTests {

    @Test
    @DisplayName("应该确保事件为不可变（Record 语义）")
    void shouldEnsureEventIsImmutable() {
      // Given
      Long sliceId = 1001L;
      Long planId = 2001L;
      String oldStatus = "EXECUTING";
      String newStatus = "SUCCEEDED";
      Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(sliceId, planId, oldStatus, newStatus, occurredAt);

      // Then - Record 是不可变的，所有字段都是 final 的
      assertThat(event.sliceId()).isEqualTo(sliceId);
      assertThat(event.planId()).isEqualTo(planId);
      assertThat(event.oldStatus()).isEqualTo(oldStatus);
      assertThat(event.newStatus()).isEqualTo(newStatus);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);

      // 无法修改字段（编译时保证）
      // event.sliceId = 9999L; // 编译错误
    }

    @Test
    @DisplayName("应该保护时间戳不被外部修改")
    void shouldProtectTimestampFromExternalModification() {
      // Given
      Instant originalTime = Instant.parse("2024-01-15T10:30:00Z");
      SliceStatusChangedEvent event =
          new SliceStatusChangedEvent(1001L, 2001L, "EXECUTING", "SUCCEEDED", originalTime);

      // When - 获取时间戳
      Instant retrievedTime = event.occurredAt();

      // Then - Instant 是不可变的，无法修改
      assertThat(retrievedTime).isEqualTo(originalTime);
      // retrievedTime.plusSeconds(100); // 返回新实例，不影响原对象
      assertThat(event.occurredAt()).isEqualTo(originalTime);
    }
  }

  // ========== 事件链测试 ==========

  @Nested
  @DisplayName("事件链场景（TaskCompletedEvent → SliceStatusChangedEvent）")
  class EventChainScenarioTests {

    @Test
    @DisplayName("应该支持事件链：多个 Task 完成触发单个 Slice 状态变更")
    void shouldSupportEventChainMultipleTasksToSingleSlice() {
      // Given - 模拟多个 Task 完成后，聚合触发 Slice 状态变更
      Long sliceId = 1001L;
      Long planId = 2001L;

      // When - Slice 基于所有子 Task 状态重新计算后变为 SUCCEEDED
      SliceStatusChangedEvent sliceEvent =
          SliceStatusChangedEvent.of(sliceId, planId, "EXECUTING", "SUCCEEDED");

      // Then
      assertThat(sliceEvent.sliceId()).isEqualTo(sliceId);
      assertThat(sliceEvent.planId()).isEqualTo(planId);
      assertThat(sliceEvent.oldStatus()).isEqualTo("EXECUTING");
      assertThat(sliceEvent.newStatus()).isEqualTo("SUCCEEDED");
      assertThat(sliceEvent.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("应该支持事件链：多个 Slice 状态变更触发 Plan 聚合状态更新")
    void shouldSupportEventChainMultipleSlicesToSinglePlan() {
      // Given - 模拟多个 Slice 状态变更后，触发 Plan 状态重新计算
      Long planId = 2001L;

      // When - 多个 Slice 变更
      SliceStatusChangedEvent slice1Event =
          SliceStatusChangedEvent.of(1001L, planId, "EXECUTING", "SUCCEEDED");
      SliceStatusChangedEvent slice2Event =
          SliceStatusChangedEvent.of(1002L, planId, "EXECUTING", "SUCCEEDED");
      SliceStatusChangedEvent slice3Event =
          SliceStatusChangedEvent.of(1003L, planId, "EXECUTING", "SUCCEEDED");

      // Then - 相同 planId 的多个事件应该触发 Plan 聚合
      assertThat(slice1Event.planId()).isEqualTo(planId);
      assertThat(slice2Event.planId()).isEqualTo(planId);
      assertThat(slice3Event.planId()).isEqualTo(planId);
    }

    @Test
    @DisplayName("应该支持部分失败场景：部分 Task 失败导致 Slice 变为 PARTIAL")
    void shouldSupportPartialFailureScenario() {
      // Given - 模拟部分 Task 失败，Slice 状态变为 PARTIAL
      Long sliceId = 1001L;
      Long planId = 2001L;

      // When
      SliceStatusChangedEvent sliceEvent =
          SliceStatusChangedEvent.of(sliceId, planId, "EXECUTING", "PARTIAL");

      // Then
      assertThat(sliceEvent.newStatus()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("应该支持全部失败场景：所有 Task 失败导致 Slice 变为 FAILED")
    void shouldSupportFullFailureScenario() {
      // Given - 模拟所有 Task 失败，Slice 状态变为 FAILED
      Long sliceId = 1001L;
      Long planId = 2001L;

      // When
      SliceStatusChangedEvent sliceEvent =
          SliceStatusChangedEvent.of(sliceId, planId, "EXECUTING", "FAILED");

      // Then
      assertThat(sliceEvent.newStatus()).isEqualTo("FAILED");
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景")
  class BusinessScenarioTests {

    @Test
    @DisplayName("应该支持审计场景：跟踪切片从创建到完成的生命周期")
    void shouldSupportAuditScenarioTrackingSliceLifecycle() {
      // Given
      Long sliceId = 1001L;
      Long planId = 2001L;

      // When - 模拟 Slice 生命周期状态变更
      SliceStatusChangedEvent createEvent =
          SliceStatusChangedEvent.of(sliceId, planId, null, "EXECUTING");
      SliceStatusChangedEvent completeEvent =
          SliceStatusChangedEvent.of(sliceId, planId, "EXECUTING", "SUCCEEDED");

      // Then
      assertThat(createEvent.oldStatus()).isNull();
      assertThat(createEvent.newStatus()).isEqualTo("EXECUTING");
      assertThat(completeEvent.oldStatus()).isEqualTo("EXECUTING");
      assertThat(completeEvent.newStatus()).isEqualTo("SUCCEEDED");
      assertThat(createEvent.occurredAt()).isBeforeOrEqualTo(completeEvent.occurredAt());
    }

    @Test
    @DisplayName("应该支持监控场景：对高切片失败率发出告警")
    void shouldSupportMonitoringScenarioForHighFailureRate() {
      // Given - 模拟多个 Slice 失败
      Long planId = 2001L;

      // When
      SliceStatusChangedEvent failedSlice1 =
          SliceStatusChangedEvent.of(1001L, planId, "EXECUTING", "FAILED");
      SliceStatusChangedEvent failedSlice2 =
          SliceStatusChangedEvent.of(1002L, planId, "EXECUTING", "FAILED");
      SliceStatusChangedEvent failedSlice3 =
          SliceStatusChangedEvent.of(1003L, planId, "EXECUTING", "FAILED");

      // Then - 监控系统应该能基于这些事件计算失败率
      assertThat(failedSlice1.newStatus()).isEqualTo("FAILED");
      assertThat(failedSlice2.newStatus()).isEqualTo("FAILED");
      assertThat(failedSlice3.newStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("应该支持指标场景：按数据源测量切片完成率")
    void shouldSupportMetricsScenarioMeasuringCompletionRate() {
      // Given
      Long planId = 2001L;

      // When
      SliceStatusChangedEvent succeededSlice1 =
          SliceStatusChangedEvent.of(1001L, planId, "EXECUTING", "SUCCEEDED");
      SliceStatusChangedEvent succeededSlice2 =
          SliceStatusChangedEvent.of(1002L, planId, "EXECUTING", "SUCCEEDED");
      SliceStatusChangedEvent failedSlice =
          SliceStatusChangedEvent.of(1003L, planId, "EXECUTING", "FAILED");

      // Then - 指标系统应该能计算完成率：2/3 成功
      long successCount =
          java.util.List.of(succeededSlice1, succeededSlice2, failedSlice).stream()
              .filter(e -> "SUCCEEDED".equals(e.newStatus()))
              .count();
      assertThat(successCount).isEqualTo(2);
    }
  }
}
