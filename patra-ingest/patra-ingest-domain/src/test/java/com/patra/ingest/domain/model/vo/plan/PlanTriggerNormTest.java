package com.patra.ingest.domain.model.vo.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link PlanTriggerNorm} 的单元测试。
///
/// 测试策略：
///
/// - 验证构造器的非空约束
///   - 验证 record 语义（equals/hashCode/toString）
///   - 验证业务方法（isHarvest/isBackfill/isUpdate）
///   - 测试边界条件和各种参数组合
///
@DisplayName("PlanTriggerNorm 值对象测试")
class PlanTriggerNormTest {

  // ==================== 测试数据工厂 ====================

  /// 创建有效的 PlanTriggerNorm 实例（包含所有可选字段）。
  private PlanTriggerNorm createValidNorm() {
    return new PlanTriggerNorm(
        1001L,
        ProvenanceCode.PUBMED,
        OperationCode.HARVEST,
        "initial",
        TriggerType.SCHEDULE,
        Scheduler.XXL,
        "job-123",
        "log-456",
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-01-31T23:59:59Z"),
        Priority.HIGH,
        Map.of("batchSize", 1000, "retryCount", 3));
  }

  /// 创建最小有效实例（所有可选字段为 null）。
  private PlanTriggerNorm createMinimalNorm() {
    return new PlanTriggerNorm(
        2002L,
        ProvenanceCode.EPMC,
        OperationCode.UPDATE,
        null, // step 可选
        TriggerType.MANUAL,
        Scheduler.SPRING,
        null, // schedulerJobId 可选
        null, // schedulerLogId 可选
        null, // requestedWindowFrom 可选
        null, // requestedWindowTo 可选
        null, // priority 可选
        null // triggerParams 可选
        );
  }

  // ==================== 构造器验证测试 ====================

  @Nested
  @DisplayName("构造器验证测试")
  class ConstructorValidationTests {

    @Test
    @DisplayName("应成功创建包含所有字段的实例")
    void shouldCreateInstanceWithAllFields() {
      // Given
      Long scheduleInstanceId = 1001L;
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      OperationCode operationCode = OperationCode.HARVEST;
      String step = "initial";
      TriggerType triggerType = TriggerType.SCHEDULE;
      Scheduler scheduler = Scheduler.XXL;
      String schedulerJobId = "job-123";
      String schedulerLogId = "log-456";
      Instant windowFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant windowTo = Instant.parse("2025-01-31T23:59:59Z");
      Priority priority = Priority.HIGH;
      Map<String, Object> triggerParams = Map.of("batchSize", 1000);

      // When
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              scheduleInstanceId,
              provenanceCode,
              operationCode,
              step,
              triggerType,
              scheduler,
              schedulerJobId,
              schedulerLogId,
              windowFrom,
              windowTo,
              priority,
              triggerParams);

      // Then
      assertThat(norm).isNotNull();
      assertThat(norm.scheduleInstanceId()).isEqualTo(scheduleInstanceId);
      assertThat(norm.provenanceCode()).isEqualTo(provenanceCode);
      assertThat(norm.operationCode()).isEqualTo(operationCode);
      assertThat(norm.step()).isEqualTo(step);
      assertThat(norm.triggerType()).isEqualTo(triggerType);
      assertThat(norm.scheduler()).isEqualTo(scheduler);
      assertThat(norm.schedulerJobId()).isEqualTo(schedulerJobId);
      assertThat(norm.schedulerLogId()).isEqualTo(schedulerLogId);
      assertThat(norm.requestedWindowFrom()).isEqualTo(windowFrom);
      assertThat(norm.requestedWindowTo()).isEqualTo(windowTo);
      assertThat(norm.priority()).isEqualTo(priority);
      assertThat(norm.triggerParams()).isEqualTo(triggerParams);
    }

    @Test
    @DisplayName("应成功创建仅包含必填字段的实例")
    void shouldCreateInstanceWithRequiredFieldsOnly() {
      // Given
      Long scheduleInstanceId = 2002L;
      ProvenanceCode provenanceCode = ProvenanceCode.EPMC;
      OperationCode operationCode = OperationCode.UPDATE;
      TriggerType triggerType = TriggerType.API;
      Scheduler scheduler = Scheduler.QUARTZ;

      // When
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              scheduleInstanceId,
              provenanceCode,
              operationCode,
              null, // 可选
              triggerType,
              scheduler,
              null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              null // 可选
              );

      // Then
      assertThat(norm).isNotNull();
      assertThat(norm.scheduleInstanceId()).isEqualTo(scheduleInstanceId);
      assertThat(norm.provenanceCode()).isEqualTo(provenanceCode);
      assertThat(norm.operationCode()).isEqualTo(operationCode);
      assertThat(norm.step()).isNull();
      assertThat(norm.triggerType()).isEqualTo(triggerType);
      assertThat(norm.scheduler()).isEqualTo(scheduler);
      assertThat(norm.schedulerJobId()).isNull();
      assertThat(norm.schedulerLogId()).isNull();
      assertThat(norm.requestedWindowFrom()).isNull();
      assertThat(norm.requestedWindowTo()).isNull();
      assertThat(norm.priority()).isNull();
      assertThat(norm.triggerParams()).isNull();
    }

    @Test
    @DisplayName("当 scheduleInstanceId 为 null 时应抛出 NullPointerException")
    void shouldThrowNullPointerExceptionWhenScheduleInstanceIdIsNull() {
      // Given
      Long scheduleInstanceId = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new PlanTriggerNorm(
                      scheduleInstanceId,
                      ProvenanceCode.PUBMED,
                      OperationCode.HARVEST,
                      "step",
                      TriggerType.SCHEDULE,
                      Scheduler.XXL,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("scheduleInstanceId不能为null");
    }

    @Test
    @DisplayName("当 provenanceCode 为 null 时应抛出 NullPointerException")
    void shouldThrowNullPointerExceptionWhenProvenanceCodeIsNull() {
      // Given
      ProvenanceCode provenanceCode = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new PlanTriggerNorm(
                      1001L,
                      provenanceCode,
                      OperationCode.HARVEST,
                      "step",
                      TriggerType.SCHEDULE,
                      Scheduler.XXL,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("provenanceCode不能为null");
    }

    @Test
    @DisplayName("当 operationCode 为 null 时应抛出 NullPointerException")
    void shouldThrowNullPointerExceptionWhenOperationCodeIsNull() {
      // Given
      OperationCode operationCode = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new PlanTriggerNorm(
                      1001L,
                      ProvenanceCode.PUBMED,
                      operationCode,
                      "step",
                      TriggerType.SCHEDULE,
                      Scheduler.XXL,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("operationCode不能为null");
    }

    @Test
    @DisplayName("当 triggerType 为 null 时应抛出 NullPointerException")
    void shouldThrowNullPointerExceptionWhenTriggerTypeIsNull() {
      // Given
      TriggerType triggerType = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new PlanTriggerNorm(
                      1001L,
                      ProvenanceCode.PUBMED,
                      OperationCode.HARVEST,
                      "step",
                      triggerType,
                      Scheduler.XXL,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("triggerType不能为null");
    }

    @Test
    @DisplayName("当 scheduler 为 null 时应抛出 NullPointerException")
    void shouldThrowNullPointerExceptionWhenSchedulerIsNull() {
      // Given
      Scheduler scheduler = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new PlanTriggerNorm(
                      1001L,
                      ProvenanceCode.PUBMED,
                      OperationCode.HARVEST,
                      "step",
                      TriggerType.SCHEDULE,
                      scheduler,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("scheduler不能为null");
    }
  }

  // ==================== Record 语义测试 ====================

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同字段值的实例应该相等")
    void shouldBeEqualWhenAllFieldsAreIdentical() {
      // Given
      PlanTriggerNorm norm1 = createValidNorm();
      PlanTriggerNorm norm2 = createValidNorm();

      // When & Then
      assertThat(norm1).isEqualTo(norm2);
      assertThat(norm1.hashCode()).isEqualTo(norm2.hashCode());
    }

    @Test
    @DisplayName("不同字段值的实例应该不相等")
    void shouldNotBeEqualWhenFieldsAreDifferent() {
      // Given
      PlanTriggerNorm norm1 = createValidNorm();
      PlanTriggerNorm norm2 =
          new PlanTriggerNorm(
              9999L, // 不同的 scheduleInstanceId
              ProvenanceCode.PUBMED,
              OperationCode.HARVEST,
              "initial",
              TriggerType.SCHEDULE,
              Scheduler.XXL,
              "job-123",
              "log-456",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-01-31T23:59:59Z"),
              Priority.HIGH,
              Map.of("batchSize", 1000, "retryCount", 3));

      // When & Then
      assertThat(norm1).isNotEqualTo(norm2);
      assertThat(norm1.hashCode()).isNotEqualTo(norm2.hashCode());
    }

    @Test
    @DisplayName("实例应该不等于 null")
    void shouldNotBeEqualToNull() {
      // Given
      PlanTriggerNorm norm = createValidNorm();

      // When & Then
      assertThat(norm).isNotEqualTo(null);
    }

    @Test
    @DisplayName("实例应该不等于不同类型的对象")
    void shouldNotBeEqualToDifferentType() {
      // Given
      PlanTriggerNorm norm = createValidNorm();
      String differentTypeObject = "Not a PlanTriggerNorm";

      // When & Then
      assertThat(norm).isNotEqualTo(differentTypeObject);
    }

    @Test
    @DisplayName("实例应该等于自身")
    void shouldBeEqualToItself() {
      // Given
      PlanTriggerNorm norm = createValidNorm();

      // When & Then
      assertThat(norm).isEqualTo(norm);
    }

    @Test
    @DisplayName("toString 应该包含所有字段信息")
    void toStringShouldContainAllFields() {
      // Given
      PlanTriggerNorm norm = createValidNorm();

      // When
      String result = norm.toString();

      // Then
      assertThat(result)
          .contains("PlanTriggerNorm")
          .contains("scheduleInstanceId=1001")
          .contains("provenanceCode=PUBMED")
          .contains("operationCode=HARVEST")
          .contains("step=initial")
          .contains("triggerType=SCHEDULE")
          .contains("scheduler=XXL")
          .contains("schedulerJobId=job-123")
          .contains("schedulerLogId=log-456")
          .contains("priority=HIGH");
    }

    @Test
    @DisplayName("包含 null 可选字段的实例相等性测试")
    void shouldBeEqualWithNullOptionalFields() {
      // Given
      PlanTriggerNorm norm1 = createMinimalNorm();
      PlanTriggerNorm norm2 = createMinimalNorm();

      // When & Then
      assertThat(norm1).isEqualTo(norm2);
      assertThat(norm1.hashCode()).isEqualTo(norm2.hashCode());
    }
  }

  // ==================== 业务方法测试 ====================

  @Nested
  @DisplayName("业务方法测试")
  class BusinessMethodTests {

    @Test
    @DisplayName("当 operationCode 为 HARVEST 时 isHarvest 应返回 true")
    void isHarvestShouldReturnTrueWhenOperationCodeIsHarvest() {
      // Given
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              1001L,
              ProvenanceCode.PUBMED,
              OperationCode.HARVEST,
              null,
              TriggerType.SCHEDULE,
              Scheduler.XXL,
              null,
              null,
              null,
              null,
              null,
              null);

      // When
      boolean result = norm.isHarvest();

      // Then
      assertThat(result).isTrue();
      assertThat(norm.isBackfill()).isFalse();
      assertThat(norm.isUpdate()).isFalse();
    }

    @Test
    @DisplayName("当 operationCode 为 BACKFILL 时 isBackfill 应返回 true")
    void isBackfillShouldReturnTrueWhenOperationCodeIsBackfill() {
      // Given
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              2002L,
              ProvenanceCode.EPMC,
              OperationCode.BACKFILL,
              null,
              TriggerType.MANUAL,
              Scheduler.SPRING,
              null,
              null,
              null,
              null,
              null,
              null);

      // When
      boolean result = norm.isBackfill();

      // Then
      assertThat(result).isTrue();
      assertThat(norm.isHarvest()).isFalse();
      assertThat(norm.isUpdate()).isFalse();
    }

    @Test
    @DisplayName("当 operationCode 为 UPDATE 时 isUpdate 应返回 true")
    void isUpdateShouldReturnTrueWhenOperationCodeIsUpdate() {
      // Given
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              3003L,
              ProvenanceCode.PMC,
              OperationCode.UPDATE,
              null,
              TriggerType.API,
              Scheduler.QUARTZ,
              null,
              null,
              null,
              null,
              null,
              null);

      // When
      boolean result = norm.isUpdate();

      // Then
      assertThat(result).isTrue();
      assertThat(norm.isHarvest()).isFalse();
      assertThat(norm.isBackfill()).isFalse();
    }

    @Test
    @DisplayName("当 operationCode 为 METRICS 时所有判断方法应返回 false")
    void allChecksShouldReturnFalseWhenOperationCodeIsMetrics() {
      // Given
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              4004L,
              ProvenanceCode.PUBMED,
              OperationCode.METRICS,
              null,
              TriggerType.SCHEDULE,
              Scheduler.XXL,
              null,
              null,
              null,
              null,
              null,
              null);

      // When & Then
      assertThat(norm.isHarvest()).isFalse();
      assertThat(norm.isBackfill()).isFalse();
      assertThat(norm.isUpdate()).isFalse();
    }
  }

  // ==================== 边界条件测试 ====================

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应支持空的 triggerParams Map")
    void shouldSupportEmptyTriggerParamsMap() {
      // Given
      Map<String, Object> emptyParams = new HashMap<>();

      // When
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              1001L,
              ProvenanceCode.PUBMED,
              OperationCode.HARVEST,
              "step",
              TriggerType.SCHEDULE,
              Scheduler.XXL,
              null,
              null,
              null,
              null,
              null,
              emptyParams);

      // Then
      assertThat(norm.triggerParams()).isEmpty();
    }

    @Test
    @DisplayName("应支持包含复杂对象的 triggerParams")
    void shouldSupportComplexObjectsInTriggerParams() {
      // Given
      Map<String, Object> complexParams =
          Map.of(
              "stringValue",
              "test",
              "intValue",
              123,
              "boolValue",
              true,
              "nestedMap",
              Map.of("key", "value"));

      // When
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              1001L,
              ProvenanceCode.PUBMED,
              OperationCode.HARVEST,
              "step",
              TriggerType.SCHEDULE,
              Scheduler.XXL,
              null,
              null,
              null,
              null,
              null,
              complexParams);

      // Then
      assertThat(norm.triggerParams())
          .containsEntry("stringValue", "test")
          .containsEntry("intValue", 123)
          .containsEntry("boolValue", true)
          .containsKey("nestedMap");
    }

    @Test
    @DisplayName("应支持时间窗口边界（同一时刻）")
    void shouldSupportSameInstantForWindowBoundaries() {
      // Given
      Instant sameInstant = Instant.parse("2025-01-15T12:00:00Z");

      // When
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              1001L,
              ProvenanceCode.PUBMED,
              OperationCode.HARVEST,
              "step",
              TriggerType.SCHEDULE,
              Scheduler.XXL,
              null,
              null,
              sameInstant,
              sameInstant,
              null,
              null);

      // Then
      assertThat(norm.requestedWindowFrom()).isEqualTo(sameInstant);
      assertThat(norm.requestedWindowTo()).isEqualTo(sameInstant);
    }

    @Test
    @DisplayName("应支持极长的 step 字符串")
    void shouldSupportVeryLongStepString() {
      // Given
      String longStep = "a".repeat(500);

      // When
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              1001L,
              ProvenanceCode.PUBMED,
              OperationCode.HARVEST,
              longStep,
              TriggerType.SCHEDULE,
              Scheduler.XXL,
              null,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(norm.step()).hasSize(500);
    }

    @Test
    @DisplayName("应支持极长的 schedulerJobId 和 schedulerLogId")
    void shouldSupportVeryLongSchedulerIds() {
      // Given
      String longJobId = "job-" + "x".repeat(200);
      String longLogId = "log-" + "y".repeat(200);

      // When
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              1001L,
              ProvenanceCode.PUBMED,
              OperationCode.HARVEST,
              "step",
              TriggerType.SCHEDULE,
              Scheduler.XXL,
              longJobId,
              longLogId,
              null,
              null,
              null,
              null);

      // Then
      assertThat(norm.schedulerJobId()).startsWith("job-").hasSize(204);
      assertThat(norm.schedulerLogId()).startsWith("log-").hasSize(204);
    }
  }

  // ==================== 枚举组合测试 ====================

  @Nested
  @DisplayName("枚举组合测试")
  class EnumCombinationTests {

    @Test
    @DisplayName("应支持所有 ProvenanceCode 枚举值")
    void shouldSupportAllProvenanceCodeValues() {
      // Given
      ProvenanceCode[] allCodes = ProvenanceCode.values();

      // When & Then
      for (ProvenanceCode code : allCodes) {
        PlanTriggerNorm norm =
            new PlanTriggerNorm(
                1001L,
                code,
                OperationCode.HARVEST,
                null,
                TriggerType.SCHEDULE,
                Scheduler.XXL,
                null,
                null,
                null,
                null,
                null,
                null);
        assertThat(norm.provenanceCode()).isEqualTo(code);
      }
    }

    @Test
    @DisplayName("应支持所有 OperationCode 枚举值")
    void shouldSupportAllOperationCodeValues() {
      // Given
      OperationCode[] allCodes = OperationCode.values();

      // When & Then
      for (OperationCode code : allCodes) {
        PlanTriggerNorm norm =
            new PlanTriggerNorm(
                1001L,
                ProvenanceCode.PUBMED,
                code,
                null,
                TriggerType.SCHEDULE,
                Scheduler.XXL,
                null,
                null,
                null,
                null,
                null,
                null);
        assertThat(norm.operationCode()).isEqualTo(code);
      }
    }

    @Test
    @DisplayName("应支持所有 TriggerType 枚举值")
    void shouldSupportAllTriggerTypeValues() {
      // Given
      TriggerType[] allTypes = TriggerType.values();

      // When & Then
      for (TriggerType type : allTypes) {
        PlanTriggerNorm norm =
            new PlanTriggerNorm(
                1001L,
                ProvenanceCode.PUBMED,
                OperationCode.HARVEST,
                null,
                type,
                Scheduler.XXL,
                null,
                null,
                null,
                null,
                null,
                null);
        assertThat(norm.triggerType()).isEqualTo(type);
      }
    }

    @Test
    @DisplayName("应支持所有 Scheduler 枚举值")
    void shouldSupportAllSchedulerValues() {
      // Given
      Scheduler[] allSchedulers = Scheduler.values();

      // When & Then
      for (Scheduler scheduler : allSchedulers) {
        PlanTriggerNorm norm =
            new PlanTriggerNorm(
                1001L,
                ProvenanceCode.PUBMED,
                OperationCode.HARVEST,
                null,
                TriggerType.SCHEDULE,
                scheduler,
                null,
                null,
                null,
                null,
                null,
                null);
        assertThat(norm.scheduler()).isEqualTo(scheduler);
      }
    }

    @Test
    @DisplayName("应支持所有 Priority 枚举值")
    void shouldSupportAllPriorityValues() {
      // Given
      Priority[] allPriorities = Priority.values();

      // When & Then
      for (Priority priority : allPriorities) {
        PlanTriggerNorm norm =
            new PlanTriggerNorm(
                1001L,
                ProvenanceCode.PUBMED,
                OperationCode.HARVEST,
                null,
                TriggerType.SCHEDULE,
                Scheduler.XXL,
                null,
                null,
                null,
                null,
                priority,
                null);
        assertThat(norm.priority()).isEqualTo(priority);
      }
    }
  }
}
