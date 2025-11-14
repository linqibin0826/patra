package com.patra.ingest.domain.model.aggregate;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.SliceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PlanSliceAggregate 单元测试。
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
 * <p>测试覆盖范围：
 *
 * <ul>
 *   <li>✅ 工厂方法测试（create/restore）
 *   <li>✅ 状态机转换测试（PENDING → ASSIGNED → FINISHED）
 *   <li>✅ 业务规则测试（bindPlan, markAssigned, updateStatus）
 *   <li>✅ 不变性测试（final 字段验证）
 *   <li>✅ 边界条件测试（null 处理）
 *   <li>✅ 聚合根基类行为测试
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("PlanSliceAggregate 单元测试")
class PlanSliceAggregateTest {

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("create() 工厂方法")
  class CreateFactoryMethodTests {

    @Test
    @DisplayName("应该成功创建新切片并初始化为 PENDING 状态")
    void shouldCreateNewSliceWithPendingStatus() {
      // Given: 切片创建参数
      Long planId = 1001L;
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      int sliceNo = 1;
      String sliceSignatureHash = "hash-slice-001";
      String windowSpecJson = "{\"from\":\"2024-01-01\",\"to\":\"2024-01-31\"}";
      String exprHash = "hash-expr-001";
      String exprSnapshotJson = "{\"expr\":\"test\"}";

      // When: 创建新切片
      PlanSliceAggregate slice =
          PlanSliceAggregate.create(
              planId,
              provenanceCode,
              sliceNo,
              sliceSignatureHash,
              windowSpecJson,
              exprHash,
              exprSnapshotJson);

      // Then: 验证初始状态
      assertThat(slice).isNotNull();
      assertThat(slice.getId()).isNull(); // 新创建的聚合根 ID 为 null
      assertThat(slice.getPlanId()).isEqualTo(planId);
      assertThat(slice.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(slice.getSliceNo()).isEqualTo(sliceNo);
      assertThat(slice.getSliceSignatureHash()).isEqualTo(sliceSignatureHash);
      assertThat(slice.getWindowSpecJson()).isEqualTo(windowSpecJson);
      assertThat(slice.getExprHash()).isEqualTo(exprHash);
      assertThat(slice.getExprSnapshotJson()).isEqualTo(exprSnapshotJson);
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.PENDING);
      assertThat(slice.isTransient()).isTrue(); // 新创建的聚合根是瞬态的
    }

    @Test
    @DisplayName("应该抛出异常当 sliceSignatureHash 为 null")
    void shouldThrowExceptionWhenSliceSignatureHashIsNull() {
      // Given: sliceSignatureHash 为 null
      String sliceSignatureHash = null;

      // When & Then: 创建切片应该失败
      assertThatThrownBy(
              () ->
                  PlanSliceAggregateTestDataBuilder.builder()
                      .sliceSignatureHash(sliceSignatureHash)
                      .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("sliceSignatureHash must not be null");
    }

    @Test
    @DisplayName("应该允许 planId 为 null（稍后通过 bindPlan 设置）")
    void shouldAllowNullPlanId() {
      // Given: planId 为 null
      Long planId = null;

      // When: 创建切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().planId(planId).build();

      // Then: 应该成功创建
      assertThat(slice).isNotNull();
      assertThat(slice.getPlanId()).isNull();
    }

    @Test
    @DisplayName("应该允许可选字段为 null")
    void shouldAllowOptionalFieldsToBeNull() {
      // Given: 可选字段为 null
      ProvenanceCode provenanceCode = null;
      String windowSpecJson = null;
      String exprHash = null;
      String exprSnapshotJson = null;

      // When: 创建切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder()
              .provenanceCode(provenanceCode)
              .windowSpecJson(windowSpecJson)
              .exprHash(exprHash)
              .exprSnapshotJson(exprSnapshotJson)
              .build();

      // Then: 应该成功创建
      assertThat(slice).isNotNull();
      assertThat(slice.getProvenanceCode()).isNull();
      assertThat(slice.getWindowSpecJson()).isNull();
      assertThat(slice.getExprHash()).isNull();
      assertThat(slice.getExprSnapshotJson()).isNull();
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreFactoryMethodTests {

    @Test
    @DisplayName("应该从持久化状态成功重建切片")
    void shouldRestoreSliceFromPersistentState() {
      // Given: 持久化状态
      Long id = 100L;
      Long planId = 1001L;
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      int sliceNo = 5;
      String sliceSignatureHash = "hash-slice-restored";
      String windowSpecJson = "{\"from\":\"2024-05-01\",\"to\":\"2024-05-31\"}";
      String exprHash = "hash-expr-restored";
      String exprSnapshotJson = "{\"expr\":\"restored\"}";
      SliceStatus status = SliceStatus.ASSIGNED;
      long version = 3L;

      // When: 从持久化恢复
      PlanSliceAggregate slice =
          PlanSliceAggregate.restore(
              id,
              planId,
              provenanceCode,
              sliceNo,
              sliceSignatureHash,
              windowSpecJson,
              exprHash,
              exprSnapshotJson,
              status,
              version);

      // Then: 验证所有状态都被正确恢复
      assertThat(slice).isNotNull();
      assertThat(slice.getId()).isEqualTo(id);
      assertThat(slice.getPlanId()).isEqualTo(planId);
      assertThat(slice.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(slice.getSliceNo()).isEqualTo(sliceNo);
      assertThat(slice.getSliceSignatureHash()).isEqualTo(sliceSignatureHash);
      assertThat(slice.getWindowSpecJson()).isEqualTo(windowSpecJson);
      assertThat(slice.getExprHash()).isEqualTo(exprHash);
      assertThat(slice.getExprSnapshotJson()).isEqualTo(exprSnapshotJson);
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.ASSIGNED);
      assertThat(slice.getVersion()).isEqualTo(version);
      assertThat(slice.isTransient()).isFalse(); // 已持久化的聚合根不是瞬态的
    }

    @Test
    @DisplayName("应该默认使用 PENDING 状态当 status 为 null")
    void shouldDefaultToPendingStatusWhenStatusIsNull() {
      // Given: status 为 null
      SliceStatus status = null;

      // When: 从持久化恢复
      PlanSliceAggregate slice =
          PlanSliceAggregate.restore(
              100L,
              1001L,
              ProvenanceCode.PUBMED,
              1,
              "hash-slice-001",
              "{}",
              "hash-expr-001",
              "{}",
              status,
              1L);

      // Then: 应该默认为 PENDING 状态
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.PENDING);
    }
  }

  // ========== 状态机测试 ==========

  @Nested
  @DisplayName("状态机转换测试")
  class StateMachineTests {

    @Test
    @DisplayName("应该允许从 PENDING 转换到 ASSIGNED（标记为已分配）")
    void shouldAllowTransitionFromPendingToAssigned() {
      // Given: 新创建的切片处于 PENDING 状态
      PlanSliceAggregate slice = PlanSliceAggregateTestDataBuilder.builder().build();
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.PENDING);

      // When: 标记为已分配（任务创建后）
      slice.markAssigned();

      // Then: 状态应该转换为 ASSIGNED
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("应该允许从 ASSIGNED 转换到 FINISHED（通过 updateStatus）")
    void shouldAllowTransitionFromAssignedToFinished() {
      // Given: 切片处于 ASSIGNED 状态
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder()
              .status(SliceStatus.ASSIGNED)
              .buildRestored();

      // When: 更新状态为 FINISHED（任务完成后）
      slice.updateStatus(SliceStatus.FINISHED);

      // Then: 状态应该转换为 FINISHED
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.FINISHED);
    }

    @Test
    @DisplayName("应该允许重复调用 markAssigned（幂等性）")
    void shouldAllowRepeatedMarkAssignedCalls() {
      // Given: 切片处于 ASSIGNED 状态
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder()
              .status(SliceStatus.ASSIGNED)
              .buildRestored();

      // When: 再次标记为已分配
      slice.markAssigned();

      // Then: 状态应该保持为 ASSIGNED（幂等）
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("应该允许任意状态转换通过 updateStatus")
    void shouldAllowArbitraryTransitionViaUpdateStatus() {
      // Given: 切片处于 FINISHED 状态
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder()
              .status(SliceStatus.FINISHED)
              .buildRestored();

      // When: 更新状态为 PENDING（虽然不是标准流程，但技术上允许）
      slice.updateStatus(SliceStatus.PENDING);

      // Then: 状态应该更新
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.PENDING);
    }

    @Test
    @DisplayName("应该抛出异常当 updateStatus() 参数为 null")
    void shouldThrowExceptionWhenUpdateStatusWithNull() {
      // Given: 任意状态的切片
      PlanSliceAggregate slice = PlanSliceAggregateTestDataBuilder.builder().build();

      // When & Then: 使用 null 更新状态应该失败
      assertThatThrownBy(() -> slice.updateStatus(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("newStatus 不能为 null");
    }
  }

  // ========== 完整状态转换流程测试 ==========

  @Nested
  @DisplayName("完整状态转换流程测试")
  class FullStateTransitionFlowTests {

    @Test
    @DisplayName("应该成功完成 PENDING → ASSIGNED → FINISHED 流程")
    void shouldCompleteFullStateTransitionFlow() {
      // Given: 新创建的切片
      PlanSliceAggregate slice = PlanSliceAggregateTestDataBuilder.builder().build();
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.PENDING);

      // When: 任务创建后标记为已分配
      slice.markAssigned();

      // Then: 应该处于 ASSIGNED 状态
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.ASSIGNED);

      // When: 任务执行完成后更新为 FINISHED
      slice.updateStatus(SliceStatus.FINISHED);

      // Then: 应该处于 FINISHED 状态
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.FINISHED);
    }
  }

  // ========== 业务规则测试 ==========

  @Nested
  @DisplayName("业务规则测试")
  class BusinessRuleTests {

    @Test
    @DisplayName("应该成功绑定计划")
    void shouldBindPlanSuccessfully() {
      // Given: 未绑定计划的切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().planId(null).build();
      assertThat(slice.getPlanId()).isNull();

      // When: 绑定计划
      Long planId = 2001L;
      slice.bindPlan(planId);

      // Then: 应该成功绑定
      assertThat(slice.getPlanId()).isEqualTo(planId);
    }

    @Test
    @DisplayName("应该抛出异常当 bindPlan() 参数为 null")
    void shouldThrowExceptionWhenBindPlanWithNull() {
      // Given: 未绑定计划的切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().planId(null).build();

      // When & Then: 使用 null 绑定计划应该失败
      assertThatThrownBy(() -> slice.bindPlan(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("planId 不能为 null");
    }

    @Test
    @DisplayName("应该允许重新绑定计划（覆盖现有值）")
    void shouldAllowRebindingPlan() {
      // Given: 已绑定计划的切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().planId(1001L).build();
      assertThat(slice.getPlanId()).isEqualTo(1001L);

      // When: 重新绑定到新计划
      Long newPlanId = 2001L;
      slice.bindPlan(newPlanId);

      // Then: 应该更新为新计划 ID
      assertThat(slice.getPlanId()).isEqualTo(newPlanId);
    }

    @Test
    @DisplayName("应该使用切片签名哈希实现幂等性")
    void shouldUseSliceSignatureHashForIdempotency() {
      // Given: 相同的参数
      String sliceSignatureHash = "hash-slice-idempotent";

      // When: 创建两个切片
      PlanSliceAggregate slice1 =
          PlanSliceAggregateTestDataBuilder.builder()
              .sliceSignatureHash(sliceSignatureHash)
              .build();

      PlanSliceAggregate slice2 =
          PlanSliceAggregateTestDataBuilder.builder()
              .sliceSignatureHash(sliceSignatureHash)
              .build();

      // Then: 切片签名哈希应该相同（用于去重）
      assertThat(slice1.getSliceSignatureHash()).isEqualTo(slice2.getSliceSignatureHash());
    }

    @Test
    @DisplayName("应该使用不同的签名哈希当参数不同")
    void shouldUseDifferentSignatureHashWhenParametersDiffer() {
      // Given: 不同的签名哈希
      String hash1 = "hash-slice-001";
      String hash2 = "hash-slice-002";

      // When: 创建两个切片
      PlanSliceAggregate slice1 =
          PlanSliceAggregateTestDataBuilder.builder().sliceSignatureHash(hash1).build();

      PlanSliceAggregate slice2 =
          PlanSliceAggregateTestDataBuilder.builder().sliceSignatureHash(hash2).build();

      // Then: 切片签名哈希应该不同
      assertThat(slice1.getSliceSignatureHash()).isNotEqualTo(slice2.getSliceSignatureHash());
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("应该保证切片核心字段在生命周期中保持不可变")
    void shouldEnsureCoreFieldsRemainsImmutableThroughLifecycle() {
      // Given: 新创建的切片
      ProvenanceCode originalProvenanceCode = ProvenanceCode.PUBMED;
      int originalSliceNo = 3;
      String originalSliceSignatureHash = "hash-original";
      String originalWindowSpecJson = "{\"from\":\"2024-03-01\",\"to\":\"2024-03-31\"}";
      String originalExprHash = "hash-expr-original";
      String originalExprSnapshotJson = "{\"expr\":\"original\"}";

      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder()
              .provenanceCode(originalProvenanceCode)
              .sliceNo(originalSliceNo)
              .sliceSignatureHash(originalSliceSignatureHash)
              .windowSpecJson(originalWindowSpecJson)
              .exprHash(originalExprHash)
              .exprSnapshotJson(originalExprSnapshotJson)
              .build();

      // When: 执行状态转换操作
      slice.markAssigned();
      slice.updateStatus(SliceStatus.FINISHED);
      slice.bindPlan(2001L); // 绑定计划

      // Then: 不可变字段应该保持不变
      assertThat(slice.getProvenanceCode()).isEqualTo(originalProvenanceCode);
      assertThat(slice.getSliceNo()).isEqualTo(originalSliceNo);
      assertThat(slice.getSliceSignatureHash()).isEqualTo(originalSliceSignatureHash);
      assertThat(slice.getWindowSpecJson()).isEqualTo(originalWindowSpecJson);
      assertThat(slice.getExprHash()).isEqualTo(originalExprHash);
      assertThat(slice.getExprSnapshotJson()).isEqualTo(originalExprSnapshotJson);
    }

    @Test
    @DisplayName("应该允许状态字段发生变化")
    void shouldAllowStatusFieldToChange() {
      // Given: 新创建的切片
      PlanSliceAggregate slice = PlanSliceAggregateTestDataBuilder.builder().build();
      SliceStatus initialStatus = slice.getStatus();

      // When: 改变状态
      slice.markAssigned();

      // Then: 状态应该发生变化
      assertThat(slice.getStatus()).isNotEqualTo(initialStatus);
      assertThat(slice.getStatus()).isEqualTo(SliceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("应该允许 planId 字段发生变化")
    void shouldAllowPlanIdFieldToChange() {
      // Given: 未绑定计划的切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().planId(null).build();

      // When: 绑定计划
      slice.bindPlan(3001L);

      // Then: planId 应该发生变化
      assertThat(slice.getPlanId()).isEqualTo(3001L);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极端切片序号边界（最小值）")
    void shouldHandleMinimumSliceNumber() {
      // Given: 极小的切片序号
      int minSliceNo = Integer.MIN_VALUE;

      // When: 创建切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().sliceNo(minSliceNo).build();

      // Then: 应该成功创建
      assertThat(slice.getSliceNo()).isEqualTo(minSliceNo);
    }

    @Test
    @DisplayName("应该处理极端切片序号边界（最大值）")
    void shouldHandleMaximumSliceNumber() {
      // Given: 极大的切片序号
      int maxSliceNo = Integer.MAX_VALUE;

      // When: 创建切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().sliceNo(maxSliceNo).build();

      // Then: 应该成功创建
      assertThat(slice.getSliceNo()).isEqualTo(maxSliceNo);
    }

    @Test
    @DisplayName("应该处理零值切片序号")
    void shouldHandleZeroSliceNumber() {
      // Given: 零值切片序号
      int zeroSliceNo = 0;

      // When: 创建切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().sliceNo(zeroSliceNo).build();

      // Then: 应该成功创建
      assertThat(slice.getSliceNo()).isEqualTo(zeroSliceNo);
    }

    @Test
    @DisplayName("应该处理负数切片序号")
    void shouldHandleNegativeSliceNumber() {
      // Given: 负数切片序号
      int negativeSliceNo = -1;

      // When: 创建切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder().sliceNo(negativeSliceNo).build();

      // Then: 应该成功创建
      assertThat(slice.getSliceNo()).isEqualTo(negativeSliceNo);
    }

    @Test
    @DisplayName("应该处理极长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Given: 极长的字符串
      String longSignatureHash = "a".repeat(1000);
      String longWindowSpecJson = "{\"data\":\"" + "x".repeat(10000) + "\"}";
      String longExprSnapshotJson = "{\"expr\":\"" + "y".repeat(10000) + "\"}";

      // When: 创建切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder()
              .sliceSignatureHash(longSignatureHash)
              .windowSpecJson(longWindowSpecJson)
              .exprSnapshotJson(longExprSnapshotJson)
              .build();

      // Then: 应该成功创建
      assertThat(slice.getSliceSignatureHash()).hasSize(1000);
      assertThat(slice.getWindowSpecJson()).contains("x".repeat(10000));
      assertThat(slice.getExprSnapshotJson()).contains("y".repeat(10000));
    }

    @Test
    @DisplayName("应该处理空字符串字段")
    void shouldHandleEmptyStringFields() {
      // Given: 空字符串（provenanceCode 改为 null，因为它是枚举类型）
      ProvenanceCode emptyProvenanceCode = null;
      String emptyWindowSpecJson = "";
      String emptyExprHash = "";
      String emptyExprSnapshotJson = "";

      // When: 创建切片
      PlanSliceAggregate slice =
          PlanSliceAggregateTestDataBuilder.builder()
              .provenanceCode(emptyProvenanceCode)
              .windowSpecJson(emptyWindowSpecJson)
              .exprHash(emptyExprHash)
              .exprSnapshotJson(emptyExprSnapshotJson)
              .build();

      // Then: 应该成功创建（业务规则允许空字符串）
      assertThat(slice.getProvenanceCode()).isNull();
      assertThat(slice.getWindowSpecJson()).isEmpty();
      assertThat(slice.getExprHash()).isEmpty();
      assertThat(slice.getExprSnapshotJson()).isEmpty();
    }
  }

  // ========== 聚合根基类行为测试 ==========

  @Nested
  @DisplayName("聚合根基类行为测试")
  class AggregateRootBehaviorTests {

    @Test
    @DisplayName("应该正确处理 ID 分配")
    void shouldHandleIdAssignment() {
      // Given: 新创建的切片
      PlanSliceAggregate slice = PlanSliceAggregateTestDataBuilder.builder().build();
      assertThat(slice.getId()).isNull();
      assertThat(slice.isTransient()).isTrue();

      // When: 分配 ID（模拟仓储保存后）
      Long assignedId = 100L;
      slice.assignId(assignedId);

      // Then: ID 应该被正确分配
      assertThat(slice.getId()).isEqualTo(assignedId);
      assertThat(slice.isTransient()).isFalse();
    }

    @Test
    @DisplayName("应该抛出异常当分配 null ID")
    void shouldThrowExceptionWhenAssigningNullId() {
      // Given: 新创建的切片
      PlanSliceAggregate slice = PlanSliceAggregateTestDataBuilder.builder().build();

      // When & Then: 分配 null ID 应该失败
      assertThatThrownBy(() -> slice.assignId(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("聚合 ID 不能为 null");
    }

    @Test
    @DisplayName("应该正确处理版本分配")
    void shouldHandleVersionAssignment() {
      // Given: 新创建的切片
      PlanSliceAggregate slice = PlanSliceAggregateTestDataBuilder.builder().build();
      assertThat(slice.getVersion()).isEqualTo(0L);

      // When: 分配版本
      long version = 5L;
      slice.assignVersion(version);

      // Then: 版本应该被正确分配
      assertThat(slice.getVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("应该抛出异常当分配负版本")
    void shouldThrowExceptionWhenAssigningNegativeVersion() {
      // Given: 新创建的切片
      PlanSliceAggregate slice = PlanSliceAggregateTestDataBuilder.builder().build();

      // When & Then: 分配负版本应该失败
      assertThatThrownBy(() -> slice.assignVersion(-1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("版本必须 >= 0");
    }
  }

  // ========== TestDataBuilder（辅助类）==========

  /**
   * PlanSliceAggregate 测试数据构建器。
   *
   * <p>遵循 Builder 模式，提供默认值以简化测试数据构建。
   */
  static class PlanSliceAggregateTestDataBuilder {
    private Long id = null; // 默认为 null（新创建的聚合根）
    private Long planId = 1001L;
    private ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
    private int sliceNo = 1;
    private String sliceSignatureHash = "hash-slice-default";
    private String windowSpecJson = "{\"from\":\"2024-01-01\",\"to\":\"2024-01-31\"}";
    private String exprHash = "hash-expr-default";
    private String exprSnapshotJson = "{\"expr\":\"default\"}";
    private SliceStatus status = SliceStatus.PENDING;
    private long version = 0L;

    public static PlanSliceAggregateTestDataBuilder builder() {
      return new PlanSliceAggregateTestDataBuilder();
    }

    public PlanSliceAggregateTestDataBuilder id(Long id) {
      this.id = id;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder planId(Long planId) {
      this.planId = planId;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder provenanceCode(ProvenanceCode provenanceCode) {
      this.provenanceCode = provenanceCode;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder sliceNo(int sliceNo) {
      this.sliceNo = sliceNo;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder sliceSignatureHash(String sliceSignatureHash) {
      this.sliceSignatureHash = sliceSignatureHash;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder windowSpecJson(String windowSpecJson) {
      this.windowSpecJson = windowSpecJson;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder exprHash(String exprHash) {
      this.exprHash = exprHash;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder exprSnapshotJson(String exprSnapshotJson) {
      this.exprSnapshotJson = exprSnapshotJson;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder status(SliceStatus status) {
      this.status = status;
      return this;
    }

    public PlanSliceAggregateTestDataBuilder version(long version) {
      this.version = version;
      return this;
    }

    /** 构建新创建的切片（使用 create() 工厂方法）。 */
    public PlanSliceAggregate build() {
      return PlanSliceAggregate.create(
          planId, provenanceCode, sliceNo, sliceSignatureHash, windowSpecJson, exprHash,
          exprSnapshotJson);
    }

    /** 构建从持久化重建的切片（使用 restore() 工厂方法）。 */
    public PlanSliceAggregate buildRestored() {
      Long restoredId = (id != null) ? id : 100L; // 默认 ID
      return PlanSliceAggregate.restore(
          restoredId,
          planId,
          provenanceCode,
          sliceNo,
          sliceSignatureHash,
          windowSpecJson,
          exprHash,
          exprSnapshotJson,
          status,
          version);
    }
  }
}
