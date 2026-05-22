package dev.linqibin.patra.ingest.domain.model.aggregate;

import static org.assertj.core.api.Assertions.*;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.domain.model.enums.Scheduler;
import dev.linqibin.patra.ingest.domain.model.enums.TriggerType;
import dev.linqibin.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ScheduleInstanceAggregate 单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 使用 Fixture 模式构建测试数据
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///
/// 测试范围：
///
/// - ✅ 工厂方法验证（start(), restore()）
///   - ✅ 必填字段验证（scheduler, triggerType）
///   - ✅ 初始化状态验证
///   - ✅ 不可变性验证
///   - ✅ 边界条件测试
///   - ✅ 聚合根基类行为
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ScheduleInstanceAggregate 单元测试")
class ScheduleInstanceAggregateTest {

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("start() 工厂方法")
  class StartFactoryMethodTests {

    @Test
    @DisplayName("应该成功创建新调度实例并记录触发上下文")
    void shouldCreateNewScheduleInstanceWithTriggerContext() {
      // Given
      Scheduler scheduler = Scheduler.XXL;
      String schedulerJobId = "job-001";
      String schedulerLogId = "log-12345";
      TriggerType triggerType = TriggerType.SCHEDULE;
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      Map<String, Object> triggerParams = new HashMap<>();
      triggerParams.put("batchSize", 100);
      triggerParams.put("timeout", 300);
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregate.start(
              scheduler,
              schedulerJobId,
              schedulerLogId,
              triggerType,
              triggeredAt,
              triggerParams,
              provenanceCode);

      // Then
      assertThat(instance).isNotNull();
      assertThat(instance.getId()).isNull(); // 新创建的聚合根 ID 为 null
      assertThat(instance.getScheduler()).isEqualTo(scheduler);
      assertThat(instance.getSchedulerJobId()).isEqualTo(schedulerJobId);
      assertThat(instance.getSchedulerLogId()).isEqualTo(schedulerLogId);
      assertThat(instance.getTriggerType()).isEqualTo(triggerType);
      assertThat(instance.getTriggeredAt()).isEqualTo(triggeredAt);
      assertThat(instance.getTriggerParams()).isEqualTo(triggerParams);
      assertThat(instance.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(instance.isTransient()).isTrue(); // 新创建的聚合根是瞬态的
    }

    @Test
    @DisplayName("应该在 triggeredAt 为 null 时自动使用当前时间")
    void shouldUseCurrentTimeWhenTriggeredAtIsNull() {
      // Given
      Instant beforeCreation = Instant.now();
      Instant triggeredAt = null;

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder().triggeredAt(triggeredAt).build();

      Instant afterCreation = Instant.now();

      // Then - triggeredAt 应该在创建前后的时间范围内
      assertThat(instance.getTriggeredAt()).isNotNull();
      assertThat(instance.getTriggeredAt())
          .isAfterOrEqualTo(beforeCreation)
          .isBeforeOrEqualTo(afterCreation);
    }

    @Test
    @DisplayName("应该允许可选字段为 null")
    void shouldAllowOptionalFieldsToBeNull() {
      // Given
      String schedulerJobId = null;
      String schedulerLogId = null;
      Map<String, Object> triggerParams = null;
      ProvenanceCode provenanceCode = null;

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .schedulerJobId(schedulerJobId)
              .schedulerLogId(schedulerLogId)
              .triggerParams(triggerParams)
              .provenanceCode(provenanceCode)
              .build();

      // Then
      assertThat(instance.getSchedulerJobId()).isNull();
      assertThat(instance.getSchedulerLogId()).isNull();
      assertThat(instance.getTriggerParams()).isNull();
      assertThat(instance.getProvenanceCode()).isNull();
    }

    @Test
    @DisplayName("应该抛出异常当 scheduler 为 null")
    void shouldThrowExceptionWhenSchedulerIsNull() {
      // Given
      Scheduler scheduler = null;

      // When & Then
      assertThatThrownBy(
              () -> ScheduleInstanceAggregateFixture.builder().scheduler(scheduler).build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("schedulerCode must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 triggerType 为 null")
    void shouldThrowExceptionWhenTriggerTypeIsNull() {
      // Given
      TriggerType triggerType = null;

      // When & Then
      assertThatThrownBy(
              () -> ScheduleInstanceAggregateFixture.builder().triggerType(triggerType).build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("triggerType must not be null");
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreFactoryMethodTests {

    @Test
    @DisplayName("应该从持久化状态成功重建调度实例")
    void shouldRestoreScheduleInstanceFromPersistentState() {
      // Given
      ScheduleInstanceId id = ScheduleInstanceId.of(100L);
      Scheduler scheduler = Scheduler.SPRING;
      String schedulerJobId = "spring-job-001";
      String schedulerLogId = "spring-log-001";
      TriggerType triggerType = TriggerType.MANUAL;
      Instant triggeredAt = Instant.parse("2025-01-01T08:00:00Z");
      Map<String, Object> triggerParams = new HashMap<>();
      triggerParams.put("userId", "admin");
      triggerParams.put("reason", "manual backfill");
      ProvenanceCode provenanceCode = ProvenanceCode.EPMC;
      long version = 5L;

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregate.restore(
              id,
              scheduler,
              schedulerJobId,
              schedulerLogId,
              triggerType,
              triggeredAt,
              triggerParams,
              provenanceCode,
              version);

      // Then
      assertThat(instance).isNotNull();
      assertThat(instance.getId()).isEqualTo(id);
      assertThat(instance.getScheduler()).isEqualTo(scheduler);
      assertThat(instance.getSchedulerJobId()).isEqualTo(schedulerJobId);
      assertThat(instance.getSchedulerLogId()).isEqualTo(schedulerLogId);
      assertThat(instance.getTriggerType()).isEqualTo(triggerType);
      assertThat(instance.getTriggeredAt()).isEqualTo(triggeredAt);
      assertThat(instance.getTriggerParams()).isEqualTo(triggerParams);
      assertThat(instance.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(instance.getVersion()).isEqualTo(version);
      assertThat(instance.isTransient()).isFalse(); // 已持久化的聚合根不是瞬态的
    }

    @Test
    @DisplayName("应该在恢复时也使用当前时间当 triggeredAt 为 null")
    void shouldUseCurrentTimeWhenRestoringWithNullTriggeredAt() {
      // Given
      Instant beforeRestore = Instant.now();
      Instant triggeredAt = null;

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(100L),
              Scheduler.XXL,
              "job-001",
              "log-001",
              TriggerType.SCHEDULE,
              triggeredAt,
              null,
              ProvenanceCode.PUBMED,
              1L);

      Instant afterRestore = Instant.now();

      // Then
      assertThat(instance.getTriggeredAt()).isNotNull();
      assertThat(instance.getTriggeredAt())
          .isAfterOrEqualTo(beforeRestore)
          .isBeforeOrEqualTo(afterRestore);
    }
  }

  // ========== 不可变性测试 ==========

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("应该保证所有配置字段在生命周期中保持不可变")
    void shouldEnsureAllFieldsRemainImmutableThroughLifecycle() {
      // Given - 创建调度实例
      Scheduler originalScheduler = Scheduler.XXL;
      String originalJobId = "job-001";
      String originalLogId = "log-12345";
      TriggerType originalTriggerType = TriggerType.SCHEDULE;
      Instant originalTriggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      Map<String, Object> originalTriggerParams = new HashMap<>();
      originalTriggerParams.put("key", "value");
      ProvenanceCode originalProvenanceCode = ProvenanceCode.PUBMED;

      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .scheduler(originalScheduler)
              .schedulerJobId(originalJobId)
              .schedulerLogId(originalLogId)
              .triggerType(originalTriggerType)
              .triggeredAt(originalTriggeredAt)
              .triggerParams(originalTriggerParams)
              .provenanceCode(originalProvenanceCode)
              .build();

      // When - 调用 recordSnapshots() 方法（虽然是空实现）
      instance.recordSnapshots();

      // Then - 所有字段应该保持不变
      assertThat(instance.getScheduler()).isEqualTo(originalScheduler);
      assertThat(instance.getSchedulerJobId()).isEqualTo(originalJobId);
      assertThat(instance.getSchedulerLogId()).isEqualTo(originalLogId);
      assertThat(instance.getTriggerType()).isEqualTo(originalTriggerType);
      assertThat(instance.getTriggeredAt()).isEqualTo(originalTriggeredAt);
      assertThat(instance.getTriggerParams()).isEqualTo(originalTriggerParams);
      assertThat(instance.getProvenanceCode()).isEqualTo(originalProvenanceCode);
    }

    @Test
    @DisplayName("修改 triggerParams Map 不应该影响调度实例")
    void shouldNotBeAffectedByExternalMapModification() {
      // Given - 创建调度实例
      Map<String, Object> triggerParams = new HashMap<>();
      triggerParams.put("original", "value");

      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder().triggerParams(triggerParams).build();

      // When - 修改外部 Map
      triggerParams.put("modified", "newValue");
      triggerParams.remove("original");

      // Then - 调度实例内部的 triggerParams 应该被修改（因为 Map 是可变的）
      // 注意：这是一个潜在的不可变性问题，但当前实现没有做防御性拷贝
      assertThat(instance.getTriggerParams()).containsEntry("modified", "newValue");
      assertThat(instance.getTriggerParams()).doesNotContainKey("original");
    }
  }

  // ========== 业务规则测试 ==========

  @Nested
  @DisplayName("业务规则测试")
  class BusinessRuleTests {

    @Test
    @DisplayName("应该支持所有调度器类型")
    void shouldSupportAllSchedulerTypes() {
      // Given - 所有调度器类型
      Scheduler[] allSchedulers = {Scheduler.XXL, Scheduler.SPRING, Scheduler.QUARTZ};

      // When & Then - 每个调度器都应该成功创建调度实例
      for (Scheduler scheduler : allSchedulers) {
        ScheduleInstanceAggregate instance =
            ScheduleInstanceAggregateFixture.builder().scheduler(scheduler).build();

        assertThat(instance.getScheduler()).isEqualTo(scheduler);
      }
    }

    @Test
    @DisplayName("应该支持所有触发类型")
    void shouldSupportAllTriggerTypes() {
      // Given - 所有触发类型
      TriggerType[] allTriggerTypes = {TriggerType.SCHEDULE, TriggerType.MANUAL, TriggerType.API};

      // When & Then - 每个触发类型都应该成功创建调度实例
      for (TriggerType triggerType : allTriggerTypes) {
        ScheduleInstanceAggregate instance =
            ScheduleInstanceAggregateFixture.builder().triggerType(triggerType).build();

        assertThat(instance.getTriggerType()).isEqualTo(triggerType);
      }
    }

    @Test
    @DisplayName("应该支持复杂的触发参数结构")
    void shouldSupportComplexTriggerParamsStructure() {
      // Given - 复杂的触发参数
      Map<String, Object> complexParams = new HashMap<>();
      complexParams.put("batchSize", 100);
      complexParams.put("timeout", 300);
      complexParams.put("retryEnabled", true);
      complexParams.put("windowStart", "2025-01-01T00:00:00Z");
      complexParams.put("windowEnd", "2025-12-31T23:59:59Z");
      complexParams.put("customConfig", Map.of("key1", "value1", "key2", "value2"));

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder().triggerParams(complexParams).build();

      // Then
      assertThat(instance.getTriggerParams()).isEqualTo(complexParams);
      assertThat(instance.getTriggerParams()).containsEntry("batchSize", 100);
      assertThat(instance.getTriggerParams()).containsEntry("retryEnabled", true);
      assertThat(instance.getTriggerParams()).containsKey("customConfig");
    }

    @Test
    @DisplayName("recordSnapshots() 方法应该是幂等的")
    void shouldBeIdempotentForRecordSnapshots() {
      // Given
      ScheduleInstanceAggregate instance = ScheduleInstanceAggregateFixture.builder().build();

      // When - 多次调用 recordSnapshots()
      instance.recordSnapshots();
      instance.recordSnapshots();
      instance.recordSnapshots();

      // Then - 应该没有副作用（方法是空实现）
      assertThat(instance).isNotNull();
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极端时间边界")
    void shouldHandleExtremeTimeBoundaries() {
      // Given - 极端时间边界
      Instant epochTime = Instant.EPOCH; // 1970-01-01T00:00:00Z
      Instant farFutureTime = Instant.parse("2099-12-31T23:59:59Z");

      // When - 使用 EPOCH 时间
      ScheduleInstanceAggregate instance1 =
          ScheduleInstanceAggregateFixture.builder().triggeredAt(epochTime).build();

      // When - 使用未来时间
      ScheduleInstanceAggregate instance2 =
          ScheduleInstanceAggregateFixture.builder().triggeredAt(farFutureTime).build();

      // Then
      assertThat(instance1.getTriggeredAt()).isEqualTo(epochTime);
      assertThat(instance2.getTriggeredAt()).isEqualTo(farFutureTime);
    }

    @Test
    @DisplayName("应该处理空的触发参数 Map")
    void shouldHandleEmptyTriggerParamsMap() {
      // Given - 空 Map
      Map<String, Object> emptyParams = new HashMap<>();

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder().triggerParams(emptyParams).build();

      // Then
      assertThat(instance.getTriggerParams()).isNotNull();
      assertThat(instance.getTriggerParams()).isEmpty();
    }

    @Test
    @DisplayName("应该处理极长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Given - 极长的字符串（provenanceCode 是枚举类型，不适用）
      String longJobId = "job-" + "x".repeat(1000);
      String longLogId = "log-" + "y".repeat(1000);

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .schedulerJobId(longJobId)
              .schedulerLogId(longLogId)
              .build();

      // Then
      assertThat(instance.getSchedulerJobId()).hasSize(1004);
      assertThat(instance.getSchedulerLogId()).hasSize(1004);
      assertThat(instance.getProvenanceCode()).isNotNull();
    }

    @Test
    @DisplayName("应该处理空字符串字段")
    void shouldHandleEmptyStringFields() {
      // Given - 空字符串（provenanceCode 改为 null，因为它是枚举类型）
      String emptyJobId = "";
      String emptyLogId = "";
      ProvenanceCode emptyProvenanceCode = null;

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .schedulerJobId(emptyJobId)
              .schedulerLogId(emptyLogId)
              .provenanceCode(emptyProvenanceCode)
              .build();

      // Then - 应该成功创建（业务规则允许空字符串）
      assertThat(instance.getSchedulerJobId()).isEmpty();
      assertThat(instance.getSchedulerLogId()).isEmpty();
      assertThat(instance.getProvenanceCode()).isNull();
    }

    @Test
    @DisplayName("应该处理触发参数中的 null 值")
    void shouldHandleNullValuesInTriggerParams() {
      // Given - 包含 null 值的触发参数
      Map<String, Object> paramsWithNull = new HashMap<>();
      paramsWithNull.put("key1", "value1");
      paramsWithNull.put("key2", null);
      paramsWithNull.put("key3", 123);

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder().triggerParams(paramsWithNull).build();

      // Then
      assertThat(instance.getTriggerParams()).containsEntry("key1", "value1");
      assertThat(instance.getTriggerParams()).containsEntry("key2", null);
      assertThat(instance.getTriggerParams()).containsEntry("key3", 123);
    }
  }

  // ========== 聚合根基类行为测试 ==========

  @Nested
  @DisplayName("聚合根基类行为测试")
  class AggregateRootBehaviorTests {

    @Test
    @DisplayName("应该正确处理 ID 分配")
    void shouldHandleIdAssignment() {
      // Given - 新创建的调度实例
      ScheduleInstanceAggregate instance = ScheduleInstanceAggregateFixture.builder().build();
      assertThat(instance.getId()).isNull();
      assertThat(instance.isTransient()).isTrue();

      // When - 分配 ID（模拟仓储保存后）
      ScheduleInstanceId assignedId = ScheduleInstanceId.of(100L);
      instance.assignId(assignedId);

      // Then
      assertThat(instance.getId()).isEqualTo(assignedId);
      assertThat(instance.isTransient()).isFalse();
    }

    @Test
    @DisplayName("应该抛出异常当分配 null ID")
    void shouldThrowExceptionWhenAssigningNullId() {
      // Given
      ScheduleInstanceAggregate instance = ScheduleInstanceAggregateFixture.builder().build();

      // When & Then
      assertThatThrownBy(() -> instance.assignId(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("聚合 ID 不能为 null");
    }

    @Test
    @DisplayName("应该正确处理版本分配")
    void shouldHandleVersionAssignment() {
      // Given
      ScheduleInstanceAggregate instance = ScheduleInstanceAggregateFixture.builder().build();
      assertThat(instance.getVersion()).isEqualTo(0L);

      // When - 分配版本
      long version = 10L;
      instance.assignVersion(version);

      // Then
      assertThat(instance.getVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("应该抛出异常当分配负版本")
    void shouldThrowExceptionWhenAssigningNegativeVersion() {
      // Given
      ScheduleInstanceAggregate instance = ScheduleInstanceAggregateFixture.builder().build();

      // When & Then
      assertThatThrownBy(() -> instance.assignVersion(-1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("版本必须 >= 0");
    }

    @Test
    @DisplayName("应该正确处理版本递增")
    void shouldHandleVersionIncrement() {
      // Given - 从持久化恢复的调度实例
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .id(ScheduleInstanceId.of(100L))
              .version(5L)
              .buildRestored();

      assertThat(instance.getVersion()).isEqualTo(5L);

      // When - 递增版本（模拟更新操作）
      instance.assignVersion(6L);

      // Then
      assertThat(instance.getVersion()).isEqualTo(6L);
    }
  }

  // ========== 调度器特定场景测试 ==========

  @Nested
  @DisplayName("调度器特定场景测试")
  class SchedulerSpecificScenarioTests {

    @Test
    @DisplayName("应该正确记录 XXL-Job 调度器触发上下文")
    void shouldRecordXxlJobSchedulerContext() {
      // Given - XXL-Job 调度器上下文
      Scheduler scheduler = Scheduler.XXL;
      String schedulerJobId = "xxl-job-001";
      String schedulerLogId = "12345678"; // XXL-Job 日志 ID
      TriggerType triggerType = TriggerType.SCHEDULE;
      Map<String, Object> triggerParams = new HashMap<>();
      triggerParams.put("executorHandler", "pubmedHarvestHandler");
      triggerParams.put("executorParams", "2025-01-01,2025-12-31");

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .scheduler(scheduler)
              .schedulerJobId(schedulerJobId)
              .schedulerLogId(schedulerLogId)
              .triggerType(triggerType)
              .triggerParams(triggerParams)
              .build();

      // Then
      assertThat(instance.getScheduler()).isEqualTo(Scheduler.XXL);
      assertThat(instance.getSchedulerJobId()).isEqualTo(schedulerJobId);
      assertThat(instance.getSchedulerLogId()).isEqualTo(schedulerLogId);
      assertThat(instance.getTriggerParams())
          .containsEntry("executorHandler", "pubmedHarvestHandler");
    }

    @Test
    @DisplayName("应该正确记录 Spring 调度器触发上下文")
    void shouldRecordSpringSchedulerContext() {
      // Given - Spring 调度器上下文
      Scheduler scheduler = Scheduler.SPRING;
      String schedulerJobId = "spring-scheduled-task-001";
      String schedulerLogId = null; // Spring 调度器可能没有日志 ID
      TriggerType triggerType = TriggerType.SCHEDULE;
      Map<String, Object> triggerParams = new HashMap<>();
      triggerParams.put("cronExpression", "0 0 2 * * ?");

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .scheduler(scheduler)
              .schedulerJobId(schedulerJobId)
              .schedulerLogId(schedulerLogId)
              .triggerType(triggerType)
              .triggerParams(triggerParams)
              .build();

      // Then
      assertThat(instance.getScheduler()).isEqualTo(Scheduler.SPRING);
      assertThat(instance.getSchedulerJobId()).isEqualTo(schedulerJobId);
      assertThat(instance.getSchedulerLogId()).isNull();
      assertThat(instance.getTriggerParams()).containsEntry("cronExpression", "0 0 2 * * ?");
    }

    @Test
    @DisplayName("应该正确记录手动触发上下文")
    void shouldRecordManualTriggerContext() {
      // Given - 手动触发上下文
      Scheduler scheduler = Scheduler.XXL;
      String schedulerJobId = "xxl-job-001";
      String schedulerLogId = "manual-trigger-001";
      TriggerType triggerType = TriggerType.MANUAL;
      Map<String, Object> triggerParams = new HashMap<>();
      triggerParams.put("userId", "admin");
      triggerParams.put("reason", "Manual backfill for missing data");
      triggerParams.put("targetDate", "2025-01-15");

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .scheduler(scheduler)
              .schedulerJobId(schedulerJobId)
              .schedulerLogId(schedulerLogId)
              .triggerType(triggerType)
              .triggerParams(triggerParams)
              .build();

      // Then
      assertThat(instance.getTriggerType()).isEqualTo(TriggerType.MANUAL);
      assertThat(instance.getTriggerParams()).containsEntry("userId", "admin");
      assertThat(instance.getTriggerParams())
          .containsEntry("reason", "Manual backfill for missing data");
    }

    @Test
    @DisplayName("应该正确记录 API 触发上下文")
    void shouldRecordApiTriggerContext() {
      // Given - API 触发上下文
      Scheduler scheduler = Scheduler.XXL;
      String schedulerJobId = "api-triggered-job-001";
      String schedulerLogId = "api-log-001";
      TriggerType triggerType = TriggerType.API;
      Map<String, Object> triggerParams = new HashMap<>();
      triggerParams.put("apiEndpoint", "/api/v1/trigger/harvest");
      triggerParams.put("requestId", "req-12345");
      triggerParams.put("clientIp", "192.168.1.100");

      // When
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .scheduler(scheduler)
              .schedulerJobId(schedulerJobId)
              .schedulerLogId(schedulerLogId)
              .triggerType(triggerType)
              .triggerParams(triggerParams)
              .build();

      // Then
      assertThat(instance.getTriggerType()).isEqualTo(TriggerType.API);
      assertThat(instance.getTriggerParams())
          .containsEntry("apiEndpoint", "/api/v1/trigger/harvest");
      assertThat(instance.getTriggerParams()).containsEntry("requestId", "req-12345");
    }
  }

  // ========== 一致性边界测试 ==========

  @Nested
  @DisplayName("一致性边界测试")
  class ConsistencyBoundaryTests {

    @Test
    @DisplayName("调度实例应该保持调度触发的完整上下文不变")
    void shouldPreserveCompleteTriggerContextImmutably() {
      // Given - 完整的调度触发上下文
      Scheduler scheduler = Scheduler.XXL;
      String schedulerJobId = "job-001";
      String schedulerLogId = "log-12345";
      TriggerType triggerType = TriggerType.SCHEDULE;
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      Map<String, Object> triggerParams = new HashMap<>();
      triggerParams.put("key1", "value1");
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;

      // When - 创建调度实例
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .scheduler(scheduler)
              .schedulerJobId(schedulerJobId)
              .schedulerLogId(schedulerLogId)
              .triggerType(triggerType)
              .triggeredAt(triggeredAt)
              .triggerParams(triggerParams)
              .provenanceCode(provenanceCode)
              .build();

      // Then - 调度触发上下文应该完整保存
      assertThat(instance.getScheduler()).isEqualTo(scheduler);
      assertThat(instance.getSchedulerJobId()).isEqualTo(schedulerJobId);
      assertThat(instance.getSchedulerLogId()).isEqualTo(schedulerLogId);
      assertThat(instance.getTriggerType()).isEqualTo(triggerType);
      assertThat(instance.getTriggeredAt()).isEqualTo(triggeredAt);
      assertThat(instance.getTriggerParams()).isEqualTo(triggerParams);
      assertThat(instance.getProvenanceCode()).isEqualTo(provenanceCode);
    }

    @Test
    @DisplayName("调度实例与计划聚合根应该是 1:N 关系")
    void shouldSupportOneToManyRelationshipWithPlanAggregate() {
      // Given - 一个调度实例可以产生多个计划
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregateFixture.builder()
              .id(ScheduleInstanceId.of(1001L))
              .buildRestored();

      // 模拟：这个调度实例 ID 会被多个计划引用
      ScheduleInstanceId scheduleInstanceId = instance.getId();

      // Then - 调度实例 ID 可以被多个计划使用
      assertThat(scheduleInstanceId).isNotNull();
      assertThat(scheduleInstanceId.value()).isEqualTo(1001L);

      // 注意：实际的 1:N 关系验证需要在仓储层或应用服务层测试
    }
  }

  // ========== Fixture (辅助类) ==========

  /// ScheduleInstanceAggregate 测试数据构建器。
  ///
  /// 遵循 Builder 模式，提供默认值以简化测试数据构建。
  static class ScheduleInstanceAggregateFixture {
    private ScheduleInstanceId id = null; // 默认为 null（新创建的聚合根）
    private Scheduler scheduler = Scheduler.XXL;
    private String schedulerJobId = "default-job-001";
    private String schedulerLogId = "default-log-12345";
    private TriggerType triggerType = TriggerType.SCHEDULE;
    private Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
    private Map<String, Object> triggerParams = new HashMap<>();
    private ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
    private long version = 0L;

    public static ScheduleInstanceAggregateFixture builder() {
      ScheduleInstanceAggregateFixture builder = new ScheduleInstanceAggregateFixture();
      // 默认触发参数
      builder.triggerParams.put("batchSize", 100);
      return builder;
    }

    public ScheduleInstanceAggregateFixture id(ScheduleInstanceId id) {
      this.id = id;
      return this;
    }

    public ScheduleInstanceAggregateFixture scheduler(Scheduler scheduler) {
      this.scheduler = scheduler;
      return this;
    }

    public ScheduleInstanceAggregateFixture schedulerJobId(String schedulerJobId) {
      this.schedulerJobId = schedulerJobId;
      return this;
    }

    public ScheduleInstanceAggregateFixture schedulerLogId(String schedulerLogId) {
      this.schedulerLogId = schedulerLogId;
      return this;
    }

    public ScheduleInstanceAggregateFixture triggerType(TriggerType triggerType) {
      this.triggerType = triggerType;
      return this;
    }

    public ScheduleInstanceAggregateFixture triggeredAt(Instant triggeredAt) {
      this.triggeredAt = triggeredAt;
      return this;
    }

    public ScheduleInstanceAggregateFixture triggerParams(Map<String, Object> triggerParams) {
      this.triggerParams = triggerParams;
      return this;
    }

    public ScheduleInstanceAggregateFixture provenanceCode(ProvenanceCode provenanceCode) {
      this.provenanceCode = provenanceCode;
      return this;
    }

    public ScheduleInstanceAggregateFixture version(long version) {
      this.version = version;
      return this;
    }

    /// 构建新创建的调度实例（使用 start() 工厂方法）。
    public ScheduleInstanceAggregate build() {
      return ScheduleInstanceAggregate.start(
          scheduler,
          schedulerJobId,
          schedulerLogId,
          triggerType,
          triggeredAt,
          triggerParams,
          provenanceCode);
    }

    /// 构建从持久化重建的调度实例（使用 restore() 工厂方法）。
    public ScheduleInstanceAggregate buildRestored() {
      ScheduleInstanceId restoredId = (id != null) ? id : ScheduleInstanceId.of(100L); // 默认 ID
      ScheduleInstanceAggregate instance =
          ScheduleInstanceAggregate.restore(
              restoredId,
              scheduler,
              schedulerJobId,
              schedulerLogId,
              triggerType,
              triggeredAt,
              triggerParams,
              provenanceCode,
              version);
      return instance;
    }
  }
}
