package com.patra.ingest.domain.event;

import com.patra.common.enums.ProvenanceCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaskQueuedEvent} 的单元测试。
 *
 * <p>测试范围：
 * <ul>
 *   <li>Record 基本语义：equals、hashCode、toString、字段访问器
 *   <li>Compact Constructor：occurredAt 自动填充
 *   <li>工厂方法：TaskQueuedEvent.of()
 *   <li>领域事件特性：实现 DomainEvent 接口
 * </ul>
 */
@DisplayName("TaskQueuedEvent 任务入队领域事件")
class TaskQueuedEventTest {

  // 测试数据常量
  private static final Long TASK_ID = 1001L;
  private static final Long PLAN_ID = 2001L;
  private static final Long SLICE_ID = 3001L;
  private static final Long SCHEDULE_INSTANCE_ID = 4001L;
  private static final ProvenanceCode PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final String OPERATION_CODE = "SEARCH";
  private static final String IDEMPOTENT_KEY = "pubmed-search-20250105-001";
  private static final String PARAMS_JSON = "{\"query\":\"cancer research\",\"maxResults\":100}";
  private static final Integer PRIORITY = 10;
  private static final Instant SCHEDULED_AT = Instant.parse("2025-01-05T10:00:00Z");
  private static final Instant OCCURRED_AT = Instant.parse("2025-01-05T09:30:00Z");

  @Nested
  @DisplayName("Record 构造与字段访问")
  class RecordConstructionAndAccessors {

    @Test
    @DisplayName("应该通过标准构造器创建事件，所有字段可访问")
    void shouldCreateEventViaCanonicalConstructor() {
      // Given: 准备所有必需参数
      // When: 使用标准构造器创建事件
      var event = new TaskQueuedEvent(
          TASK_ID,
          PLAN_ID,
          SLICE_ID,
          SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE,
          OPERATION_CODE,
          IDEMPOTENT_KEY,
          PARAMS_JSON,
          PRIORITY,
          SCHEDULED_AT,
          OCCURRED_AT
      );

      // Then: 所有字段应正确赋值
      assertThat(event.taskId()).isEqualTo(TASK_ID);
      assertThat(event.planId()).isEqualTo(PLAN_ID);
      assertThat(event.sliceId()).isEqualTo(SLICE_ID);
      assertThat(event.scheduleInstanceId()).isEqualTo(SCHEDULE_INSTANCE_ID);
      assertThat(event.provenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(event.operationCode()).isEqualTo(OPERATION_CODE);
      assertThat(event.idempotentKey()).isEqualTo(IDEMPOTENT_KEY);
      assertThat(event.paramsJson()).isEqualTo(PARAMS_JSON);
      assertThat(event.priority()).isEqualTo(PRIORITY);
      assertThat(event.scheduledAt()).isEqualTo(SCHEDULED_AT);
      assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("应该接受 null 值字段（业务可选字段）")
    void shouldAcceptNullValues() {
      // Given: 部分字段为 null
      // When: 创建事件
      var event = new TaskQueuedEvent(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          OCCURRED_AT
      );

      // Then: null 值应被正确存储
      assertThat(event.taskId()).isNull();
      assertThat(event.planId()).isNull();
      assertThat(event.sliceId()).isNull();
      assertThat(event.scheduleInstanceId()).isNull();
      assertThat(event.provenanceCode()).isNull();
      assertThat(event.operationCode()).isNull();
      assertThat(event.idempotentKey()).isNull();
      assertThat(event.paramsJson()).isNull();
      assertThat(event.priority()).isNull();
      assertThat(event.scheduledAt()).isNull();
      assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    }
  }

  @Nested
  @DisplayName("Compact Constructor 行为")
  class CompactConstructorBehavior {

    @Test
    @DisplayName("当 occurredAt 为 null 时，应该自动填充当前时间")
    void shouldAutoPopulateOccurredAtWhenNull() {
      // Given: occurredAt 为 null
      Instant beforeCreation = Instant.now();

      // When: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID,
          PLAN_ID,
          SLICE_ID,
          SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE,
          OPERATION_CODE,
          IDEMPOTENT_KEY,
          PARAMS_JSON,
          PRIORITY,
          SCHEDULED_AT,
          null // occurredAt 为 null
      );

      Instant afterCreation = Instant.now();

      // Then: occurredAt 应该被自动填充为当前时间
      assertThat(event.occurredAt())
          .isNotNull()
          .isAfterOrEqualTo(beforeCreation)
          .isBeforeOrEqualTo(afterCreation);
    }

    @Test
    @DisplayName("当 occurredAt 已提供时，应该保留原值")
    void shouldPreserveProvidedOccurredAt() {
      // Given: 提供了 occurredAt
      Instant providedTime = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID,
          PLAN_ID,
          SLICE_ID,
          SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE,
          OPERATION_CODE,
          IDEMPOTENT_KEY,
          PARAMS_JSON,
          PRIORITY,
          SCHEDULED_AT,
          providedTime
      );

      // Then: occurredAt 应该保留提供的值
      assertThat(event.occurredAt()).isEqualTo(providedTime);
    }
  }

  @Nested
  @DisplayName("工厂方法 of()")
  class FactoryMethod {

    @Test
    @DisplayName("应该创建事件并自动填充 occurredAt")
    void shouldCreateEventWithAutoPopulatedOccurredAt() {
      // Given: 准备参数（不包含 occurredAt）
      Instant beforeCreation = Instant.now();

      // When: 使用工厂方法创建事件
      var event = TaskQueuedEvent.of(
          TASK_ID,
          PLAN_ID,
          SLICE_ID,
          SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE,
          OPERATION_CODE,
          IDEMPOTENT_KEY,
          PARAMS_JSON,
          PRIORITY,
          SCHEDULED_AT
      );

      Instant afterCreation = Instant.now();

      // Then: 所有字段应正确赋值
      assertThat(event.taskId()).isEqualTo(TASK_ID);
      assertThat(event.planId()).isEqualTo(PLAN_ID);
      assertThat(event.sliceId()).isEqualTo(SLICE_ID);
      assertThat(event.scheduleInstanceId()).isEqualTo(SCHEDULE_INSTANCE_ID);
      assertThat(event.provenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(event.operationCode()).isEqualTo(OPERATION_CODE);
      assertThat(event.idempotentKey()).isEqualTo(IDEMPOTENT_KEY);
      assertThat(event.paramsJson()).isEqualTo(PARAMS_JSON);
      assertThat(event.priority()).isEqualTo(PRIORITY);
      assertThat(event.scheduledAt()).isEqualTo(SCHEDULED_AT);

      // And: occurredAt 应该被自动设置为当前时间
      assertThat(event.occurredAt())
          .isNotNull()
          .isAfterOrEqualTo(beforeCreation)
          .isBeforeOrEqualTo(afterCreation);
    }

    @Test
    @DisplayName("应该接受 null 值参数")
    void shouldAcceptNullParameters() {
      // Given: 所有参数为 null
      // When: 使用工厂方法创建事件
      var event = TaskQueuedEvent.of(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null
      );

      // Then: null 值应被正确存储
      assertThat(event.taskId()).isNull();
      assertThat(event.planId()).isNull();
      assertThat(event.sliceId()).isNull();
      assertThat(event.scheduleInstanceId()).isNull();
      assertThat(event.provenanceCode()).isNull();
      assertThat(event.operationCode()).isNull();
      assertThat(event.idempotentKey()).isNull();
      assertThat(event.paramsJson()).isNull();
      assertThat(event.priority()).isNull();
      assertThat(event.scheduledAt()).isNull();

      // And: occurredAt 应该被自动填充
      assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("应该创建不同 occurredAt 的多个实例")
    void shouldCreateMultipleInstancesWithDifferentOccurredAt() throws InterruptedException {
      // Given: 创建第一个事件
      var event1 = TaskQueuedEvent.of(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT
      );

      // When: 等待一小段时间后创建第二个事件
      Thread.sleep(10); // 确保时间戳不同

      var event2 = TaskQueuedEvent.of(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT
      );

      // Then: 两个事件的 occurredAt 应该不同
      assertThat(event2.occurredAt()).isAfter(event1.occurredAt());
    }
  }

  @Nested
  @DisplayName("Record equals() 语义")
  class EqualsSemantics {

    @Test
    @DisplayName("相同字段值的事件应该相等")
    void shouldBeEqualWhenAllFieldsMatch() {
      // Given: 创建两个字段值相同的事件
      var event1 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      var event2 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该相等
      assertThat(event1).isEqualTo(event2);
      assertThat(event2).isEqualTo(event1);
    }

    @Test
    @DisplayName("自反性：事件应该等于自身")
    void shouldBeEqualToItself() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该等于自身
      assertThat(event).isEqualTo(event);
    }

    @Test
    @DisplayName("不同 taskId 的事件应该不相等")
    void shouldNotBeEqualWhenTaskIdDiffers() {
      // Given: 创建两个 taskId 不同的事件
      var event1 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      var event2 = new TaskQueuedEvent(
          9999L, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 不应该相等
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 occurredAt 的事件应该不相等")
    void shouldNotBeEqualWhenOccurredAtDiffers() {
      // Given: 创建两个 occurredAt 不同的事件
      var event1 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      var event2 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT,
          Instant.parse("2025-01-05T10:00:00Z") // 不同的时间
      );

      // Then: 不应该相等
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 idempotentKey 的事件应该不相等")
    void shouldNotBeEqualWhenIdempotentKeyDiffers() {
      // Given: 创建两个 idempotentKey 不同的事件
      var event1 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      var event2 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, "different-key",
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 不应该相等
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("与 null 比较应该返回 false")
    void shouldNotBeEqualToNull() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 不应该等于 null
      assertThat(event).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与不同类型对象比较应该返回 false")
    void shouldNotBeEqualToDifferentType() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 不应该等于不同类型的对象
      assertThat(event).isNotEqualTo("TaskQueuedEvent");
      assertThat(event).isNotEqualTo(123);
    }

    @Test
    @DisplayName("所有字段为 null 的事件应该相等")
    void shouldBeEqualWhenAllFieldsAreNull() {
      // Given: 创建两个所有字段为 null 的事件（除了 occurredAt 会自动填充）
      Instant now = Instant.now();
      var event1 = new TaskQueuedEvent(
          null, null, null, null, null, null, null, null, null, null, now
      );

      var event2 = new TaskQueuedEvent(
          null, null, null, null, null, null, null, null, null, null, now
      );

      // Then: 应该相等
      assertThat(event1).isEqualTo(event2);
    }
  }

  @Nested
  @DisplayName("Record hashCode() 语义")
  class HashCodeSemantics {

    @Test
    @DisplayName("相同字段值的事件应该有相同的 hashCode")
    void shouldHaveSameHashCodeWhenAllFieldsMatch() {
      // Given: 创建两个字段值相同的事件
      var event1 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      var event2 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: hashCode 应该相同
      assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("不同字段值的事件应该有不同的 hashCode（大概率）")
    void shouldHaveDifferentHashCodeWhenFieldsDiffer() {
      // Given: 创建两个字段值不同的事件
      var event1 = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      var event2 = new TaskQueuedEvent(
          9999L, 8888L, 7777L, 6666L,
          ProvenanceCode.EPMC, "FETCH", "different-key",
          "{\"other\":\"params\"}", 5,
          Instant.parse("2025-01-06T10:00:00Z"),
          Instant.parse("2025-01-06T09:00:00Z")
      );

      // Then: hashCode 应该不同（大概率）
      assertThat(event1.hashCode()).isNotEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("多次调用 hashCode() 应该返回一致的值")
    void shouldReturnConsistentHashCode() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // When: 多次调用 hashCode()
      int hashCode1 = event.hashCode();
      int hashCode2 = event.hashCode();
      int hashCode3 = event.hashCode();

      // Then: 应该返回相同的值
      assertThat(hashCode1).isEqualTo(hashCode2).isEqualTo(hashCode3);
    }
  }

  @Nested
  @DisplayName("Record toString() 语义")
  class ToStringSemantics {

    @Test
    @DisplayName("toString() 应该包含类名")
    void shouldIncludeClassName() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // When: 调用 toString()
      String result = event.toString();

      // Then: 应该包含类名
      assertThat(result).contains("TaskQueuedEvent");
    }

    @Test
    @DisplayName("toString() 应该包含所有字段名和值")
    void shouldIncludeAllFieldNamesAndValues() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // When: 调用 toString()
      String result = event.toString();

      // Then: 应该包含所有字段名和值
      assertThat(result)
          .contains("taskId=" + TASK_ID)
          .contains("planId=" + PLAN_ID)
          .contains("sliceId=" + SLICE_ID)
          .contains("scheduleInstanceId=" + SCHEDULE_INSTANCE_ID)
          .contains("provenanceCode=" + PROVENANCE_CODE)
          .contains("operationCode=" + OPERATION_CODE)
          .contains("idempotentKey=" + IDEMPOTENT_KEY)
          .contains("paramsJson=" + PARAMS_JSON)
          .contains("priority=" + PRIORITY)
          .contains("scheduledAt=" + SCHEDULED_AT)
          .contains("occurredAt=" + OCCURRED_AT);
    }

    @Test
    @DisplayName("toString() 应该正确处理 null 值")
    void shouldHandleNullValues() {
      // Given: 创建部分字段为 null 的事件
      var event = new TaskQueuedEvent(
          null, null, null, null, null, null, null, null, null, null, OCCURRED_AT
      );

      // When: 调用 toString()
      String result = event.toString();

      // Then: 应该包含 "null" 字符串
      assertThat(result)
          .contains("taskId=null")
          .contains("planId=null")
          .contains("sliceId=null")
          .contains("scheduleInstanceId=null")
          .contains("provenanceCode=null")
          .contains("operationCode=null")
          .contains("idempotentKey=null")
          .contains("paramsJson=null")
          .contains("priority=null")
          .contains("scheduledAt=null")
          .contains("occurredAt=" + OCCURRED_AT);
    }

    @Test
    @DisplayName("toString() 应该返回非空字符串")
    void shouldReturnNonEmptyString() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // When: 调用 toString()
      String result = event.toString();

      // Then: 应该返回非空字符串
      assertThat(result).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("领域事件特性")
  class DomainEventCharacteristics {

    @Test
    @DisplayName("应该实现 DomainEvent 接口")
    void shouldImplementDomainEventInterface() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该是 DomainEvent 的实例
      assertThat(event).isInstanceOf(com.patra.common.domain.DomainEvent.class);
    }

    @Test
    @DisplayName("occurredAt() 方法应该返回事件发生时间")
    void shouldReturnOccurredAtTimestamp() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // When: 调用 occurredAt() 方法
      Instant result = event.occurredAt();

      // Then: 应该返回正确的时间戳
      assertThat(result).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("应该是不可变对象（immutable）")
    void shouldBeImmutable() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: Record 类型确保了不可变性
      // 所有字段都是 final 的，没有 setter 方法
      assertThat(event.taskId()).isEqualTo(TASK_ID);
      assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);

      // Note: Java Record 自动保证不可变性，无需手动验证
    }

    @Test
    @DisplayName("应该是可序列化的（实现 Serializable）")
    void shouldBeSerializable() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该是 Serializable 的实例（通过 DomainEvent 接口）
      assertThat(event).isInstanceOf(java.io.Serializable.class);
    }
  }

  @Nested
  @DisplayName("业务语义验证")
  class BusinessSemantics {

    @Test
    @DisplayName("idempotentKey 应该作为幂等性保证的唯一键")
    void shouldUseIdempotentKeyForDeduplication() {
      // Given: 创建两个 idempotentKey 相同但其他字段不同的事件
      var event1 = new TaskQueuedEvent(
          1L, 2L, 3L, 4L, ProvenanceCode.PUBMED, "SEARCH",
          "same-idempotent-key",
          "{\"a\":1}", 10, SCHEDULED_AT, OCCURRED_AT
      );

      var event2 = new TaskQueuedEvent(
          100L, 200L, 300L, 400L, ProvenanceCode.EPMC, "FETCH",
          "same-idempotent-key",
          "{\"b\":2}", 5,
          Instant.parse("2025-01-06T10:00:00Z"),
          Instant.parse("2025-01-06T09:00:00Z")
      );

      // Then: idempotentKey 应该相同
      assertThat(event1.idempotentKey()).isEqualTo(event2.idempotentKey());

      // But: 整个事件对象不相等（因为其他字段不同）
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("scheduledAt 应该表示计划执行时间")
    void shouldRepresentPlannedExecutionTime() {
      // Given: 创建事件，scheduledAt 晚于 occurredAt
      Instant occurredAt = Instant.parse("2025-01-05T09:00:00Z");
      Instant scheduledAt = Instant.parse("2025-01-05T10:00:00Z");

      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, scheduledAt, occurredAt
      );

      // Then: scheduledAt 应该晚于 occurredAt（正常业务场景）
      assertThat(event.scheduledAt()).isAfter(event.occurredAt());
    }

    @Test
    @DisplayName("priority 应该用于任务优先级排序")
    void shouldUsePriorityForTaskOrdering() {
      // Given: 创建三个不同优先级的事件
      var lowPriority = new TaskQueuedEvent(
          1L, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, "key-1",
          PARAMS_JSON, 1, SCHEDULED_AT, OCCURRED_AT
      );

      var mediumPriority = new TaskQueuedEvent(
          2L, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, "key-2",
          PARAMS_JSON, 5, SCHEDULED_AT, OCCURRED_AT
      );

      var highPriority = new TaskQueuedEvent(
          3L, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, "key-3",
          PARAMS_JSON, 10, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: priority 应该可以用于排序
      assertThat(highPriority.priority())
          .isGreaterThan(mediumPriority.priority())
          .isGreaterThan(lowPriority.priority());
    }

    @Test
    @DisplayName("paramsJson 应该包含 JSON 格式的任务参数")
    void shouldContainJsonFormattedParameters() {
      // Given: 创建包含 JSON 参数的事件
      String jsonParams = "{\"query\":\"test\",\"limit\":100,\"offset\":0}";

      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          jsonParams, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: paramsJson 应该包含完整的 JSON 字符串
      assertThat(event.paramsJson())
          .isEqualTo(jsonParams)
          .contains("query")
          .contains("limit")
          .contains("offset");
    }

    @Test
    @DisplayName("taskId、planId、sliceId、scheduleInstanceId 应该形成完整的层级关系")
    void shouldFormHierarchicalRelationship() {
      // Given: 创建事件
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该包含完整的层级标识
      assertThat(event.scheduleInstanceId()).isNotNull(); // 顶层：调度实例
      assertThat(event.planId()).isNotNull();             // 第二层：计划
      assertThat(event.sliceId()).isNotNull();            // 第三层：切片
      assertThat(event.taskId()).isNotNull();             // 第四层：任务

      // And: 所有层级 ID 应该是正数
      assertThat(event.taskId()).isPositive();
      assertThat(event.planId()).isPositive();
      assertThat(event.sliceId()).isPositive();
      assertThat(event.scheduleInstanceId()).isPositive();
    }

    @Test
    @DisplayName("provenanceCode 和 operationCode 应该用于指标维度统计")
    void shouldProvideMetricDimensions() {
      // Given: 创建两个不同来源和操作的事件
      var pubmedSearch = new TaskQueuedEvent(
          1L, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          ProvenanceCode.PUBMED, "SEARCH",
          "key-1", PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      var epmcFetch = new TaskQueuedEvent(
          2L, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          ProvenanceCode.EPMC, "FETCH",
          "key-2", PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该能够按来源和操作进行分组统计
      assertThat(pubmedSearch.provenanceCode()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(pubmedSearch.operationCode()).isEqualTo("SEARCH");
      assertThat(epmcFetch.provenanceCode()).isEqualTo(ProvenanceCode.EPMC);
      assertThat(epmcFetch.operationCode()).isEqualTo("FETCH");

      // And: 不同的组合应该代表不同的指标维度
      assertThat(pubmedSearch.provenanceCode())
          .isNotEqualTo(epmcFetch.provenanceCode());
      assertThat(pubmedSearch.operationCode())
          .isNotEqualTo(epmcFetch.operationCode());
    }
  }

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @Test
    @DisplayName("应该接受空字符串字段")
    void shouldAcceptEmptyStrings() {
      // Given: 字符串字段为空（ProvenanceCode 为 null，其他为空字符串）
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          null, "", "", "", PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 空字符串应被正确存储
      assertThat(event.provenanceCode()).isNull();
      assertThat(event.operationCode()).isEmpty();
      assertThat(event.idempotentKey()).isEmpty();
      assertThat(event.paramsJson()).isEmpty();
    }

    @Test
    @DisplayName("应该接受负数 priority")
    void shouldAcceptNegativePriority() {
      // Given: priority 为负数
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, -10, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 负数 priority 应被正确存储
      assertThat(event.priority()).isNegative().isEqualTo(-10);
    }

    @Test
    @DisplayName("应该接受 Integer.MAX_VALUE 作为 priority")
    void shouldAcceptMaxIntegerPriority() {
      // Given: priority 为最大整数
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, Integer.MAX_VALUE, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该正确存储
      assertThat(event.priority()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受 Long.MAX_VALUE 作为 ID 字段")
    void shouldAcceptMaxLongIds() {
      // Given: ID 字段为最大 Long 值
      var event = new TaskQueuedEvent(
          Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该正确存储
      assertThat(event.taskId()).isEqualTo(Long.MAX_VALUE);
      assertThat(event.planId()).isEqualTo(Long.MAX_VALUE);
      assertThat(event.sliceId()).isEqualTo(Long.MAX_VALUE);
      assertThat(event.scheduleInstanceId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受过去和未来的 Instant 时间戳")
    void shouldAcceptPastAndFutureInstants() {
      // Given: 创建过去和未来的时间戳
      Instant past = Instant.parse("2000-01-01T00:00:00Z");
      Instant future = Instant.parse("2100-12-31T23:59:59Z");

      var eventPast = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, past, past
      );

      var eventFuture = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          PARAMS_JSON, PRIORITY, future, future
      );

      // Then: 应该正确存储
      assertThat(eventPast.scheduledAt()).isEqualTo(past);
      assertThat(eventPast.occurredAt()).isEqualTo(past);
      assertThat(eventFuture.scheduledAt()).isEqualTo(future);
      assertThat(eventFuture.occurredAt()).isEqualTo(future);
    }

    @Test
    @DisplayName("应该接受超长 JSON 字符串作为 paramsJson")
    void shouldAcceptVeryLongJsonString() {
      // Given: 创建超长 JSON 字符串（1000+ 字符）
      StringBuilder longJson = new StringBuilder("{");
      for (int i = 0; i < 100; i++) {
        longJson.append("\"field").append(i).append("\":\"value").append(i).append("\"");
        if (i < 99) longJson.append(",");
      }
      longJson.append("}");

      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, IDEMPOTENT_KEY,
          longJson.toString(), PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该正确存储完整的 JSON
      assertThat(event.paramsJson()).hasSize(longJson.length());
      assertThat(event.paramsJson()).startsWith("{\"field0");
      assertThat(event.paramsJson()).endsWith("}");
    }

    @Test
    @DisplayName("应该接受包含特殊字符的 idempotentKey")
    void shouldAcceptSpecialCharactersInIdempotentKey() {
      // Given: idempotentKey 包含特殊字符
      String specialKey = "key-with-special-chars:@#$%^&*()_+-=[]{}|;':\",./<>?";

      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          PROVENANCE_CODE, OPERATION_CODE, specialKey,
          PARAMS_JSON, PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: 应该正确存储
      assertThat(event.idempotentKey()).isEqualTo(specialKey);
    }

    @Test
    @DisplayName("应该接受包含 Unicode 字符的字符串字段")
    void shouldAcceptUnicodeCharacters() {
      // Given: 字符串字段包含 Unicode 字符
      var event = new TaskQueuedEvent(
          TASK_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID,
          null, "搜索操作",
          "幂等键-🔑", "{\"查询\":\"癌症研究\",\"结果数\":100}",
          PRIORITY, SCHEDULED_AT, OCCURRED_AT
      );

      // Then: Unicode 字符应被正确存储
      assertThat(event.provenanceCode()).isNull();
      assertThat(event.operationCode()).isEqualTo("搜索操作");
      assertThat(event.idempotentKey()).contains("🔑");
      assertThat(event.paramsJson()).contains("癌症研究");
    }
  }
}
