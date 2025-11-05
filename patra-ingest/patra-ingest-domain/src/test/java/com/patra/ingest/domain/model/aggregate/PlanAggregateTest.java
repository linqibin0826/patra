package com.patra.ingest.domain.model.aggregate;

import static org.assertj.core.api.Assertions.*;

import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PlanAggregate 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>使用 TestDataBuilder 模式构建测试数据
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("PlanAggregate 单元测试")
class PlanAggregateTest {

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("create() 工厂方法")
  class CreateFactoryMethodTests {

    @Test
    @DisplayName("应该成功创建新计划并初始化为 DRAFT 状态")
    void shouldCreateNewPlanWithDraftStatus() {
      // Given
      Long scheduleInstanceId = 1001L;
      String planKey = "pubmed_harvest_2024-01-01_2024-12-31";
      String provenanceCode = "pubmed";
      String operationCode = "HARVEST";
      String exprProtoHash = "hash123";
      String exprProtoSnapshotJson = "{\"expr\":\"test\"}";
      String provenanceConfigSnapshotJson = "{\"config\":\"test\"}";
      String provenanceConfigHash = "configHash123";
      WindowSpec windowSpec = WindowSpec.ofTime(
          Instant.parse("2024-01-01T00:00:00Z"),
          Instant.parse("2024-12-31T23:59:59Z")
      );
      String sliceStrategyCode = "TIME";
      String sliceParamsJson = "{\"granularity\":\"MONTH\"}";

      // When
      PlanAggregate plan = PlanAggregate.create(
          scheduleInstanceId,
          planKey,
          provenanceCode,
          operationCode,
          exprProtoHash,
          exprProtoSnapshotJson,
          provenanceConfigSnapshotJson,
          provenanceConfigHash,
          windowSpec,
          sliceStrategyCode,
          sliceParamsJson
      );

      // Then
      assertThat(plan).isNotNull();
      assertThat(plan.getId()).isNull(); // 新创建的聚合根 ID 为 null
      assertThat(plan.getScheduleInstanceId()).isEqualTo(scheduleInstanceId);
      assertThat(plan.getPlanKey()).isEqualTo(planKey);
      assertThat(plan.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(plan.getOperationCode()).isEqualTo("HARVEST");
      assertThat(plan.getOperation()).isEqualTo(OperationCode.HARVEST);
      assertThat(plan.getExprProtoHash()).isEqualTo(exprProtoHash);
      assertThat(plan.getExprProtoSnapshotJson()).isEqualTo(exprProtoSnapshotJson);
      assertThat(plan.getProvenanceConfigSnapshotJson()).isEqualTo(provenanceConfigSnapshotJson);
      assertThat(plan.getProvenanceConfigHash()).isEqualTo(provenanceConfigHash);
      assertThat(plan.getWindowSpec()).isEqualTo(windowSpec);
      assertThat(plan.getSliceStrategyCode()).isEqualTo(sliceStrategyCode);
      assertThat(plan.getSliceParamsJson()).isEqualTo(sliceParamsJson);
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
      assertThat(plan.isTransient()).isTrue(); // 新创建的聚合根是瞬态的
    }

    @Test
    @DisplayName("应该正确规范化 operationCode 大小写")
    void shouldNormalizeOperationCodeCase() {
      // Given - 小写的 operationCode
      String operationCode = "harvest"; // 小写

      // When
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .operationCode(operationCode)
          .build();

      // Then - 应该规范化为枚举
      assertThat(plan.getOperation()).isEqualTo(OperationCode.HARVEST);
      assertThat(plan.getOperationCode()).isEqualTo("HARVEST"); // 输出总是大写
    }

    @Test
    @DisplayName("应该允许 operationCode 为 null")
    void shouldAllowNullOperationCode() {
      // Given
      String operationCode = null;

      // When
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .operationCode(operationCode)
          .build();

      // Then
      assertThat(plan.getOperation()).isNull();
      assertThat(plan.getOperationCode()).isNull();
    }

    @Test
    @DisplayName("应该抛出异常当 scheduleInstanceId 为 null")
    void shouldThrowExceptionWhenScheduleInstanceIdIsNull() {
      // Given
      Long scheduleInstanceId = null;

      // When & Then
      assertThatThrownBy(() -> PlanAggregateTestDataBuilder.builder()
          .scheduleInstanceId(scheduleInstanceId)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("scheduleInstanceId must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 planKey 为 null")
    void shouldThrowExceptionWhenPlanKeyIsNull() {
      // Given
      String planKey = null;

      // When & Then
      assertThatThrownBy(() -> PlanAggregateTestDataBuilder.builder()
          .planKey(planKey)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("planKey must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 windowSpec 为 null")
    void shouldThrowExceptionWhenWindowSpecIsNull() {
      // Given
      WindowSpec windowSpec = null;

      // When & Then
      assertThatThrownBy(() -> PlanAggregateTestDataBuilder.builder()
          .windowSpec(windowSpec)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("windowSpec must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 operationCode 无效")
    void shouldThrowExceptionWhenOperationCodeIsInvalid() {
      // Given
      String operationCode = "INVALID_OPERATION";

      // When & Then
      assertThatThrownBy(() -> PlanAggregateTestDataBuilder.builder()
          .operationCode(operationCode)
          .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的操作代码");
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreFactoryMethodTests {

    @Test
    @DisplayName("应该从持久化状态成功重建计划")
    void shouldRestorePlanFromPersistentState() {
      // Given
      Long id = 100L;
      Long scheduleInstanceId = 1001L;
      String planKey = "pubmed_harvest_2024-01-01_2024-12-31";
      String provenanceCode = "pubmed";
      String operationCode = "HARVEST";
      String exprProtoHash = "hash123";
      String exprProtoSnapshotJson = "{\"expr\":\"test\"}";
      String provenanceConfigSnapshotJson = "{\"config\":\"test\"}";
      String provenanceConfigHash = "configHash123";
      WindowSpec windowSpec = WindowSpec.ofTime(
          Instant.parse("2024-01-01T00:00:00Z"),
          Instant.parse("2024-12-31T23:59:59Z")
      );
      String sliceStrategyCode = "TIME";
      String sliceParamsJson = "{\"granularity\":\"MONTH\"}";
      PlanStatus status = PlanStatus.READY;
      long version = 3L;

      // When
      PlanAggregate plan = PlanAggregate.restore(
          id,
          scheduleInstanceId,
          planKey,
          provenanceCode,
          operationCode,
          exprProtoHash,
          exprProtoSnapshotJson,
          provenanceConfigSnapshotJson,
          provenanceConfigHash,
          windowSpec,
          sliceStrategyCode,
          sliceParamsJson,
          status,
          version
      );

      // Then
      assertThat(plan).isNotNull();
      assertThat(plan.getId()).isEqualTo(id);
      assertThat(plan.getScheduleInstanceId()).isEqualTo(scheduleInstanceId);
      assertThat(plan.getPlanKey()).isEqualTo(planKey);
      assertThat(plan.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(plan.getOperationCode()).isEqualTo("HARVEST");
      assertThat(plan.getOperation()).isEqualTo(OperationCode.HARVEST);
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.READY);
      assertThat(plan.getVersion()).isEqualTo(version);
      assertThat(plan.isTransient()).isFalse(); // 已持久化的聚合根不是瞬态的
    }

    @Test
    @DisplayName("应该默认使用 DRAFT 状态当 status 为 null")
    void shouldDefaultToDraftStatusWhenStatusIsNull() {
      // Given
      PlanStatus status = null;

      // When
      PlanAggregate plan = PlanAggregate.restore(
          100L,
          1001L,
          "planKey",
          "pubmed",
          "HARVEST",
          "hash",
          "{}",
          "{}",
          "hash",
          WindowSpec.ofSingle(),
          "SINGLE",
          "{}",
          status,
          1L
      );

      // Then
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
    }
  }

  // ========== 状态机测试 ==========

  @Nested
  @DisplayName("状态机转换测试")
  class StateMachineTests {

    @Test
    @DisplayName("应该允许从 DRAFT 转换到 SLICING")
    void shouldAllowTransitionFromDraftToSlicing() {
      // Given - 新创建的计划处于 DRAFT 状态
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder().build();
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);

      // When - 开始切片生成
      plan.startSlicing();

      // Then - 状态应该转换为 SLICING
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.SLICING);
    }

    @Test
    @DisplayName("应该抛出异常当从非 DRAFT 状态调用 startSlicing()")
    void shouldThrowExceptionWhenStartSlicingFromNonDraftStatus() {
      // Given - 计划已经处于 SLICING 状态
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .status(PlanStatus.SLICING)
          .buildRestored();

      // When & Then
      assertThatThrownBy(() -> plan.startSlicing())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("计划状态无效，无法开始切片生成");
    }

    @Test
    @DisplayName("应该抛出异常当从 READY 状态调用 startSlicing()")
    void shouldThrowExceptionWhenStartSlicingFromReadyStatus() {
      // Given - 计划已经处于 READY 状态
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .status(PlanStatus.READY)
          .buildRestored();

      // When & Then
      assertThatThrownBy(() -> plan.startSlicing())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("计划状态无效，无法开始切片生成");
    }

    @Test
    @DisplayName("应该抛出异常当从 ARCHIVED 状态调用 startSlicing()")
    void shouldThrowExceptionWhenStartSlicingFromArchivedStatus() {
      // Given - 计划已经处于 ARCHIVED 状态
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .status(PlanStatus.ARCHIVED)
          .buildRestored();

      // When & Then
      assertThatThrownBy(() -> plan.startSlicing())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("计划状态无效，无法开始切片生成");
    }

    @Test
    @DisplayName("应该允许将计划标记为 READY")
    void shouldAllowMarkingPlanAsReady() {
      // Given - 计划处于 SLICING 状态
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .status(PlanStatus.SLICING)
          .buildRestored();

      // When - 标记为就绪
      plan.markReady();

      // Then - 状态应该转换为 READY
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.READY);
    }

    @Test
    @DisplayName("应该允许更新状态为任意值")
    void shouldAllowUpdatingStatusToAnyValue() {
      // Given - 计划处于 READY 状态
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .status(PlanStatus.READY)
          .buildRestored();

      // When - 更新状态为 ARCHIVED
      plan.updateStatus(PlanStatus.ARCHIVED);

      // Then - 状态应该更新
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.ARCHIVED);
    }

    @Test
    @DisplayName("应该抛出异常当 updateStatus() 参数为 null")
    void shouldThrowExceptionWhenUpdateStatusWithNull() {
      // Given
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder().build();

      // When & Then
      assertThatThrownBy(() -> plan.updateStatus(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("newStatus 不能为 null");
    }
  }

  // ========== 完整状态转换流程测试 ==========

  @Nested
  @DisplayName("完整状态转换流程测试")
  class FullStateTransitionFlowTests {

    @Test
    @DisplayName("应该成功完成 DRAFT → SLICING → READY 流程")
    void shouldCompleteFullStateTransitionFlow() {
      // Given - 新创建的计划
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder().build();
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);

      // When - 开始切片生成
      plan.startSlicing();

      // Then - 应该处于 SLICING 状态
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.SLICING);

      // When - 切片生成完成
      plan.markReady();

      // Then - 应该处于 READY 状态
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.READY);

      // When - 任务执行完成后归档
      plan.updateStatus(PlanStatus.ARCHIVED);

      // Then - 应该处于 ARCHIVED 状态
      assertThat(plan.getStatus()).isEqualTo(PlanStatus.ARCHIVED);
    }
  }

  // ========== 不可变性测试 ==========

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("应该保证计划配置在生命周期中保持不可变")
    void shouldEnsurePlanConfigurationRemainsImmutableThroughLifecycle() {
      // Given - 新创建的计划
      String originalPlanKey = "pubmed_harvest_2024-01-01_2024-12-31";
      String originalProvenanceCode = "pubmed";
      String originalExprProtoHash = "hash123";
      WindowSpec originalWindowSpec = WindowSpec.ofTime(
          Instant.parse("2024-01-01T00:00:00Z"),
          Instant.parse("2024-12-31T23:59:59Z")
      );

      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .planKey(originalPlanKey)
          .provenanceCode(originalProvenanceCode)
          .exprProtoHash(originalExprProtoHash)
          .windowSpec(originalWindowSpec)
          .build();

      // When - 状态转换
      plan.startSlicing();
      plan.markReady();
      plan.updateStatus(PlanStatus.ARCHIVED);

      // Then - 配置应该保持不变
      assertThat(plan.getPlanKey()).isEqualTo(originalPlanKey);
      assertThat(plan.getProvenanceCode()).isEqualTo(originalProvenanceCode);
      assertThat(plan.getExprProtoHash()).isEqualTo(originalExprProtoHash);
      assertThat(plan.getWindowSpec()).isEqualTo(originalWindowSpec);
    }
  }

  // ========== WindowSpec 访问器测试 ==========

  @Nested
  @DisplayName("WindowSpec 便捷访问器测试")
  class WindowSpecAccessorTests {

    @Test
    @DisplayName("应该返回时间窗口边界当使用 TIME 策略")
    void shouldReturnTimeBoundariesForTimeStrategy() {
      // Given - 使用 TIME 策略的计划
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-12-31T23:59:59Z");
      WindowSpec windowSpec = WindowSpec.ofTime(from, to);

      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .windowSpec(windowSpec)
          .build();

      // When & Then
      assertThat(plan.getWindowFrom()).isEqualTo(from);
      assertThat(plan.getWindowTo()).isEqualTo(to);
    }

    @Test
    @DisplayName("应该返回 null 当使用非 TIME 策略（SINGLE）")
    void shouldReturnNullForNonTimeStrategy_Single() {
      // Given - 使用 SINGLE 策略的计划
      WindowSpec windowSpec = WindowSpec.ofSingle();

      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .windowSpec(windowSpec)
          .build();

      // When & Then
      assertThat(plan.getWindowFrom()).isNull();
      assertThat(plan.getWindowTo()).isNull();
    }

    @Test
    @DisplayName("应该返回 null 当使用非 TIME 策略（ID_RANGE）")
    void shouldReturnNullForNonTimeStrategy_IdRange() {
      // Given - 使用 ID_RANGE 策略的计划
      WindowSpec windowSpec = WindowSpec.ofIdRange(1000L, 2000L);

      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .windowSpec(windowSpec)
          .build();

      // When & Then
      assertThat(plan.getWindowFrom()).isNull();
      assertThat(plan.getWindowTo()).isNull();
    }

    @Test
    @DisplayName("应该返回 null 当使用非 TIME 策略（CURSOR_LANDMARK）")
    void shouldReturnNullForNonTimeStrategy_CursorLandmark() {
      // Given - 使用 CURSOR_LANDMARK 策略的计划
      WindowSpec windowSpec = WindowSpec.ofCursor("token1", "token2");

      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .windowSpec(windowSpec)
          .build();

      // When & Then
      assertThat(plan.getWindowFrom()).isNull();
      assertThat(plan.getWindowTo()).isNull();
    }

    @Test
    @DisplayName("应该返回 null 当使用非 TIME 策略（VOLUME_BUDGET）")
    void shouldReturnNullForNonTimeStrategy_VolumeBudget() {
      // Given - 使用 VOLUME_BUDGET 策略的计划
      WindowSpec windowSpec = WindowSpec.ofVolume(10000, "RECORDS");

      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .windowSpec(windowSpec)
          .build();

      // When & Then
      assertThat(plan.getWindowFrom()).isNull();
      assertThat(plan.getWindowTo()).isNull();
    }
  }

  // ========== 幂等性测试 ==========

  @Nested
  @DisplayName("幂等性测试")
  class IdempotencyTests {

    @Test
    @DisplayName("应该使用相同的 planKey 创建计划以实现幂等性")
    void shouldUseSamePlanKeyForIdempotency() {
      // Given - 相同的参数
      String planKey = "pubmed_harvest_2024-01-01_2024-12-31_TIME";

      // When - 创建两个计划
      PlanAggregate plan1 = PlanAggregateTestDataBuilder.builder()
          .planKey(planKey)
          .build();

      PlanAggregate plan2 = PlanAggregateTestDataBuilder.builder()
          .planKey(planKey)
          .build();

      // Then - planKey 应该相同（用于去重）
      assertThat(plan1.getPlanKey()).isEqualTo(plan2.getPlanKey());
    }

    @Test
    @DisplayName("应该使用不同的 planKey 当参数不同")
    void shouldUseDifferentPlanKeyWhenParametersDiffer() {
      // Given - 不同的参数
      String planKey1 = "pubmed_harvest_2024-01-01_2024-12-31_TIME";
      String planKey2 = "epmc_backfill_2024-01-01_2024-12-31_DATE";

      // When - 创建两个计划
      PlanAggregate plan1 = PlanAggregateTestDataBuilder.builder()
          .planKey(planKey1)
          .build();

      PlanAggregate plan2 = PlanAggregateTestDataBuilder.builder()
          .planKey(planKey2)
          .build();

      // Then - planKey 应该不同
      assertThat(plan1.getPlanKey()).isNotEqualTo(plan2.getPlanKey());
    }
  }

  // ========== 业务规则测试 ==========

  @Nested
  @DisplayName("业务规则测试")
  class BusinessRuleTests {

    @Test
    @DisplayName("应该允许可选字段为 null")
    void shouldAllowOptionalFieldsToBeNull() {
      // Given - 可选字段为 null
      String provenanceCode = null;
      String operationCode = null;
      String exprProtoHash = null;
      String exprProtoSnapshotJson = null;
      String provenanceConfigSnapshotJson = null;
      String provenanceConfigHash = null;
      String sliceStrategyCode = null;
      String sliceParamsJson = null;

      // When
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .provenanceCode(provenanceCode)
          .operationCode(operationCode)
          .exprProtoHash(exprProtoHash)
          .exprProtoSnapshotJson(exprProtoSnapshotJson)
          .provenanceConfigSnapshotJson(provenanceConfigSnapshotJson)
          .provenanceConfigHash(provenanceConfigHash)
          .sliceStrategyCode(sliceStrategyCode)
          .sliceParamsJson(sliceParamsJson)
          .build();

      // Then - 应该成功创建
      assertThat(plan).isNotNull();
      assertThat(plan.getProvenanceCode()).isNull();
      assertThat(plan.getOperationCode()).isNull();
      assertThat(plan.getExprProtoHash()).isNull();
      assertThat(plan.getExprProtoSnapshotJson()).isNull();
      assertThat(plan.getProvenanceConfigSnapshotJson()).isNull();
      assertThat(plan.getProvenanceConfigHash()).isNull();
      assertThat(plan.getSliceStrategyCode()).isNull();
      assertThat(plan.getSliceParamsJson()).isNull();
    }

    @Test
    @DisplayName("应该正确处理所有 OperationCode 枚举值")
    void shouldHandleAllOperationCodeEnumValues() {
      // Given - 所有 OperationCode 枚举值
      OperationCode[] allOperations = {
          OperationCode.HARVEST,
          OperationCode.BACKFILL,
          OperationCode.UPDATE,
          OperationCode.METRICS
      };

      // When & Then - 每个操作都应该成功创建计划
      for (OperationCode operation : allOperations) {
        PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
            .operationCode(operation.getCode())
            .build();

        assertThat(plan.getOperation()).isEqualTo(operation);
        assertThat(plan.getOperationCode()).isEqualTo(operation.getCode());
      }
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极端时间窗口边界")
    void shouldHandleExtremeTimeBoundaries() {
      // Given - 极端时间边界
      Instant from = Instant.parse("1970-01-01T00:00:00Z"); // Unix Epoch
      Instant to = Instant.parse("2099-12-31T23:59:59Z"); // 远期

      WindowSpec windowSpec = WindowSpec.ofTime(from, to);

      // When
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .windowSpec(windowSpec)
          .build();

      // Then
      assertThat(plan.getWindowFrom()).isEqualTo(from);
      assertThat(plan.getWindowTo()).isEqualTo(to);
    }

    @Test
    @DisplayName("应该处理极端 ID 范围边界")
    void shouldHandleExtremeIdRangeBoundaries() {
      // Given - 极端 ID 范围
      Long from = 1L;
      Long to = Long.MAX_VALUE;

      WindowSpec windowSpec = WindowSpec.ofIdRange(from, to);

      // When
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .windowSpec(windowSpec)
          .build();

      // Then
      assertThat(plan.getWindowSpec()).isEqualTo(windowSpec);
    }

    @Test
    @DisplayName("应该处理极长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Given - 极长的字符串
      String longPlanKey = "a".repeat(1000);
      String longExprProtoSnapshotJson = "{\"expr\":\"" + "x".repeat(10000) + "\"}";

      // When
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .planKey(longPlanKey)
          .exprProtoSnapshotJson(longExprProtoSnapshotJson)
          .build();

      // Then
      assertThat(plan.getPlanKey()).hasSize(1000);
      assertThat(plan.getExprProtoSnapshotJson()).contains("x".repeat(10000));
    }

    @Test
    @DisplayName("应该处理空字符串字段")
    void shouldHandleEmptyStringFields() {
      // Given - 空字符串
      String emptyProvenanceCode = "";
      String emptyExprProtoHash = "";

      // When
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder()
          .provenanceCode(emptyProvenanceCode)
          .exprProtoHash(emptyExprProtoHash)
          .build();

      // Then - 应该成功创建（业务规则允许空字符串）
      assertThat(plan.getProvenanceCode()).isEmpty();
      assertThat(plan.getExprProtoHash()).isEmpty();
    }
  }

  // ========== 聚合根基类行为测试 ==========

  @Nested
  @DisplayName("聚合根基类行为测试")
  class AggregateRootBehaviorTests {

    @Test
    @DisplayName("应该正确处理 ID 分配")
    void shouldHandleIdAssignment() {
      // Given - 新创建的计划
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder().build();
      assertThat(plan.getId()).isNull();
      assertThat(plan.isTransient()).isTrue();

      // When - 分配 ID（模拟仓储保存后）
      Long assignedId = 100L;
      plan.assignId(assignedId);

      // Then
      assertThat(plan.getId()).isEqualTo(assignedId);
      assertThat(plan.isTransient()).isFalse();
    }

    @Test
    @DisplayName("应该抛出异常当分配 null ID")
    void shouldThrowExceptionWhenAssigningNullId() {
      // Given
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder().build();

      // When & Then
      assertThatThrownBy(() -> plan.assignId(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("聚合 ID 不能为 null");
    }

    @Test
    @DisplayName("应该正确处理版本分配")
    void shouldHandleVersionAssignment() {
      // Given
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder().build();
      assertThat(plan.getVersion()).isEqualTo(0L);

      // When - 分配版本
      long version = 5L;
      plan.assignVersion(version);

      // Then
      assertThat(plan.getVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("应该抛出异常当分配负版本")
    void shouldThrowExceptionWhenAssigningNegativeVersion() {
      // Given
      PlanAggregate plan = PlanAggregateTestDataBuilder.builder().build();

      // When & Then
      assertThatThrownBy(() -> plan.assignVersion(-1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("版本必须 >= 0");
    }
  }

  // ========== TestDataBuilder (辅助类) ==========

  /**
   * PlanAggregate 测试数据构建器。
   *
   * <p>遵循 Builder 模式，提供默认值以简化测试数据构建。
   */
  static class PlanAggregateTestDataBuilder {
    private Long id = null; // 默认为 null（新创建的聚合根）
    private Long scheduleInstanceId = 1001L;
    private String planKey = "pubmed_harvest_2024-01-01_2024-12-31";
    private String provenanceCode = "pubmed";
    private String operationCode = "HARVEST";
    private String exprProtoHash = "hash123";
    private String exprProtoSnapshotJson = "{\"expr\":\"test\"}";
    private String provenanceConfigSnapshotJson = "{\"config\":\"test\"}";
    private String provenanceConfigHash = "configHash123";
    private WindowSpec windowSpec = WindowSpec.ofTime(
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-12-31T23:59:59Z")
    );
    private String sliceStrategyCode = "TIME";
    private String sliceParamsJson = "{\"granularity\":\"MONTH\"}";
    private PlanStatus status = PlanStatus.DRAFT;
    private long version = 0L;

    public static PlanAggregateTestDataBuilder builder() {
      return new PlanAggregateTestDataBuilder();
    }

    public PlanAggregateTestDataBuilder id(Long id) {
      this.id = id;
      return this;
    }

    public PlanAggregateTestDataBuilder scheduleInstanceId(Long scheduleInstanceId) {
      this.scheduleInstanceId = scheduleInstanceId;
      return this;
    }

    public PlanAggregateTestDataBuilder planKey(String planKey) {
      this.planKey = planKey;
      return this;
    }

    public PlanAggregateTestDataBuilder provenanceCode(String provenanceCode) {
      this.provenanceCode = provenanceCode;
      return this;
    }

    public PlanAggregateTestDataBuilder operationCode(String operationCode) {
      this.operationCode = operationCode;
      return this;
    }

    public PlanAggregateTestDataBuilder exprProtoHash(String exprProtoHash) {
      this.exprProtoHash = exprProtoHash;
      return this;
    }

    public PlanAggregateTestDataBuilder exprProtoSnapshotJson(String exprProtoSnapshotJson) {
      this.exprProtoSnapshotJson = exprProtoSnapshotJson;
      return this;
    }

    public PlanAggregateTestDataBuilder provenanceConfigSnapshotJson(
        String provenanceConfigSnapshotJson) {
      this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
      return this;
    }

    public PlanAggregateTestDataBuilder provenanceConfigHash(String provenanceConfigHash) {
      this.provenanceConfigHash = provenanceConfigHash;
      return this;
    }

    public PlanAggregateTestDataBuilder windowSpec(WindowSpec windowSpec) {
      this.windowSpec = windowSpec;
      return this;
    }

    public PlanAggregateTestDataBuilder sliceStrategyCode(String sliceStrategyCode) {
      this.sliceStrategyCode = sliceStrategyCode;
      return this;
    }

    public PlanAggregateTestDataBuilder sliceParamsJson(String sliceParamsJson) {
      this.sliceParamsJson = sliceParamsJson;
      return this;
    }

    public PlanAggregateTestDataBuilder status(PlanStatus status) {
      this.status = status;
      return this;
    }

    public PlanAggregateTestDataBuilder version(long version) {
      this.version = version;
      return this;
    }

    /**
     * 构建新创建的计划（使用 create() 工厂方法）。
     */
    public PlanAggregate build() {
      return PlanAggregate.create(
          scheduleInstanceId,
          planKey,
          provenanceCode,
          operationCode,
          exprProtoHash,
          exprProtoSnapshotJson,
          provenanceConfigSnapshotJson,
          provenanceConfigHash,
          windowSpec,
          sliceStrategyCode,
          sliceParamsJson
      );
    }

    /**
     * 构建从持久化重建的计划（使用 restore() 工厂方法）。
     */
    public PlanAggregate buildRestored() {
      Long restoredId = (id != null) ? id : 100L; // 默认 ID
      return PlanAggregate.restore(
          restoredId,
          scheduleInstanceId,
          planKey,
          provenanceCode,
          operationCode,
          exprProtoHash,
          exprProtoSnapshotJson,
          provenanceConfigSnapshotJson,
          provenanceConfigHash,
          windowSpec,
          sliceStrategyCode,
          sliceParamsJson,
          status,
          version
      );
    }
  }
}
