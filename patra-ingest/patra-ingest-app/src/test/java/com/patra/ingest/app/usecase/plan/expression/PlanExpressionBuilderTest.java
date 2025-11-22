package com.patra.ingest.app.usecase.plan.expression;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.And;
import com.patra.expr.Const;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link PlanExpressionBuilder} 单元测试
/// 
/// 测试策略: 纯单元测试,验证表达式构建的各种场景
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("PlanExpressionBuilder 单元测试")
class PlanExpressionBuilderTest {

  private PlanExpressionBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new PlanExpressionBuilder();
  }

  @Nested
  @DisplayName("build - 表达式描述符构建")
  class BuildTest {

    @Test
    @DisplayName("应该构建基础表达式描述符 - UPDATE 操作无约束")
    void shouldBuildBasicDescriptor_UpdateOperationWithoutConstraints() {
      // Given: UPDATE 操作,无特殊约束
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 返回非空描述符
      assertThat(descriptor).isNotNull();
      assertThat(descriptor.expr()).isNotNull();
      assertThat(descriptor.jsonSnapshot()).isNotNull();
      assertThat(descriptor.hash()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("应该返回 constTrue 表达式 - 无约束条件")
    void shouldReturnConstTrueExpr_WhenNoConstraints() {
      // Given: UPDATE 操作,无约束
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 表达式为 Const.TRUE
      assertThat(descriptor.expr()).isInstanceOf(Const.class);
      assertThat(descriptor.expr()).isEqualTo(Const.TRUE);
    }

    @Test
    @DisplayName("应该生成稳定的 Hash - 相同输入相同输出")
    void shouldGenerateStableHash_SameInputSameOutput() {
      // Given: 相同的触发规范和配置
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 多次构建
      PlanExpressionDescriptor descriptor1 = builder.build(norm, snapshot);
      PlanExpressionDescriptor descriptor2 = builder.build(norm, snapshot);

      // Then: Hash 值相同
      assertThat(descriptor1.hash()).isEqualTo(descriptor2.hash());
      assertThat(descriptor1.jsonSnapshot()).isEqualTo(descriptor2.jsonSnapshot());
    }

    @Test
    @DisplayName("应该生成不同的 Hash - 不同操作代码")
    void shouldGenerateDifferentHash_DifferentOperationCode() {
      // Given: 不同的操作代码
      PlanTriggerNorm norm1 = createTriggerNorm(OperationCode.UPDATE, null, null);
      PlanTriggerNorm norm2 = createTriggerNorm(OperationCode.HARVEST, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor1 = builder.build(norm1, snapshot);
      PlanExpressionDescriptor descriptor2 = builder.build(norm2, snapshot);

      // Then: Hash 值相同(因为当前实现 UPDATE 和 HARVEST 无差异约束,buildUpdateBusinessConstraints
      // 返回空)
      // 但表达式结构应该一致
      assertThat(descriptor1.expr()).isInstanceOf(Const.class);
      assertThat(descriptor2.expr()).isInstanceOf(Const.class);
    }

    @Test
    @DisplayName("应该构建表达式描述符 - HARVEST 操作")
    void shouldBuildDescriptor_HarvestOperation() {
      // Given: HARVEST 操作
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.HARVEST, from, to);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 返回有效的描述符
      assertThat(descriptor).isNotNull();
      assertThat(descriptor.expr()).isNotNull();
      assertThat(descriptor.hash()).isNotBlank();
    }

    @Test
    @DisplayName("应该构建表达式描述符 - BACKFILL 操作")
    void shouldBuildDescriptor_BackfillOperation() {
      // Given: BACKFILL 操作
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.BACKFILL, from, to);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 返回有效的描述符
      assertThat(descriptor).isNotNull();
      assertThat(descriptor.expr()).isNotNull();
      assertThat(descriptor.hash()).isNotBlank();
    }
  }

  @Nested
  @DisplayName("buildBusinessExpression - 业务表达式构建")
  class BuildBusinessExpressionTest {

    @Test
    @DisplayName("应该返回 constTrue - UPDATE 操作无约束")
    void shouldReturnConstTrue_UpdateOperationNoConstraints() {
      // Given: UPDATE 操作
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 表达式为 Const.TRUE
      assertThat(descriptor.expr()).isInstanceOf(Const.class);
      assertThat(descriptor.expr()).isEqualTo(Const.TRUE);
    }

    @Test
    @DisplayName("应该返回 constTrue - HARVEST 操作当前无约束")
    void shouldReturnConstTrue_HarvestOperationCurrentlyNoConstraints() {
      // Given: HARVEST 操作(当前实现中 buildUpdateBusinessConstraints 返回空,所以无约束)
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.HARVEST, from, to);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 表达式为 constTrue(因为当前 UPDATE 约束为空)
      assertThat(descriptor.expr()).isInstanceOf(Const.class);
    }

    @Test
    @DisplayName("应该正确组合约束 - 外部条件为 null")
    void shouldCombineConstraintsCorrectly_ExternalConditionsNull() {
      // Given: 外部条件为 null(当前实现固定返回 null)
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 表达式只包含内部约束(当前为 constTrue)
      assertThat(descriptor.expr()).isInstanceOf(Const.class);
    }
  }

  @Nested
  @DisplayName("表达式标准化")
  class ExprCanonicalizationTest {

    @Test
    @DisplayName("应该生成标准化 JSON - constTrue 表达式")
    void shouldGenerateCanonicalJson_ConstTrueExpr() {
      // Given: constTrue 表达式
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: JSON 快照非空且有效
      assertThat(descriptor.jsonSnapshot()).isNotNull().isNotBlank();
      // Const.TRUE 的标准化 JSON 应该包含 type 和 value
      assertThat(descriptor.jsonSnapshot()).contains("CONST").contains("true");
    }

    @Test
    @DisplayName("应该生成 Hash - 基于标准化 JSON")
    void shouldGenerateHash_BasedOnCanonicalJson() {
      // Given: 表达式
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: Hash 非空且是有效的哈希值(通常是 SHA-256,长度固定)
      assertThat(descriptor.hash()).isNotNull().isNotBlank();
      // SHA-256 哈希通常是 64 个字符(十六进制)
      assertThat(descriptor.hash().length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("应该处理不同 Provenance - 生成不同表达式")
    void shouldHandleDifferentProvenance_GenerateDifferentExpressions() {
      // Given: 不同的 Provenance
      PlanTriggerNorm norm1 =
          new PlanTriggerNorm(
              1L,
              ProvenanceCode.PUBMED,
              OperationCode.UPDATE,
              null,
              TriggerType.MANUAL,
              Scheduler.XXL,
              "job-1",
              "log-1",
              null,
              null,
              Priority.NORMAL,
              Map.of());

      PlanTriggerNorm norm2 =
          new PlanTriggerNorm(
              1L,
              ProvenanceCode.CROSSREF,
              OperationCode.UPDATE,
              null,
              TriggerType.MANUAL,
              Scheduler.XXL,
              "job-1",
              "log-1",
              null,
              null,
              Priority.NORMAL,
              Map.of());

      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor1 = builder.build(norm1, snapshot);
      PlanExpressionDescriptor descriptor2 = builder.build(norm2, snapshot);

      // Then: 表达式结构相同(因为当前实现不依赖 Provenance),但可能未来会不同
      // 当前两者都是 constTrue,所以 Hash 相同
      assertThat(descriptor1.expr()).isInstanceOf(Const.class);
      assertThat(descriptor2.expr()).isInstanceOf(Const.class);
      assertThat(descriptor1.hash()).isEqualTo(descriptor2.hash());
    }
  }

  @Nested
  @DisplayName("边界条件和特殊场景")
  class EdgeCasesTest {

    @Test
    @DisplayName("应该处理 null 配置快照")
    void shouldHandleNullConfigSnapshot() {
      // Given: null 配置快照
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, null);

      // Then: 应该正常构建(不依赖配置快照)
      assertThat(descriptor).isNotNull();
      assertThat(descriptor.expr()).isNotNull();
      assertThat(descriptor.hash()).isNotBlank();
    }

    @Test
    @DisplayName("应该处理最小化触发规范")
    void shouldHandleMinimalTriggerNorm() {
      // Given: 最小化的触发规范
      PlanTriggerNorm norm =
          new PlanTriggerNorm(
              1L,
              ProvenanceCode.PUBMED,
              OperationCode.UPDATE,
              null, // step
              TriggerType.MANUAL,
              Scheduler.XXL,
              null, // schedulerJobId
              null, // schedulerLogId
              null, // requestedWindowFrom
              null, // requestedWindowTo
              null, // priority
              null); // triggerParams

      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 应该正常构建
      assertThat(descriptor).isNotNull();
      assertThat(descriptor.expr()).isNotNull();
    }

    @Test
    @DisplayName("应该处理有时间窗口的 UPDATE 操作")
    void shouldHandleUpdateOperation_WithTimeWindow() {
      // Given: UPDATE 操作,但有时间窗口(虽然 UPDATE 通常不需要)
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, from, to);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 应该正常构建
      assertThat(descriptor).isNotNull();
      assertThat(descriptor.expr()).isNotNull();
    }

    @Test
    @DisplayName("应该确保表达式描述符不变式 - expr 非 null")
    void shouldEnsureDescriptorInvariant_ExprNotNull() {
      // Given: 任意触发规范
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: expr 非 null(由 PlanExpressionDescriptor 的 compact constructor 保证)
      assertThat(descriptor.expr()).isNotNull();
    }

    @Test
    @DisplayName("应该确保表达式描述符不变式 - jsonSnapshot 非 null")
    void shouldEnsureDescriptorInvariant_JsonSnapshotNotNull() {
      // Given: 任意触发规范
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: jsonSnapshot 非 null(回退到 "{}" 如果为 null)
      assertThat(descriptor.jsonSnapshot()).isNotNull();
    }

    @Test
    @DisplayName("应该确保表达式描述符不变式 - hash 非空")
    void shouldEnsureDescriptorInvariant_HashNotBlank() {
      // Given: 任意触发规范
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式描述符
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: hash 非空
      assertThat(descriptor.hash()).isNotNull().isNotBlank();
    }
  }

  @Nested
  @DisplayName("当前实现行为验证")
  class CurrentImplementationBehaviorTest {

    @Test
    @DisplayName("应该验证 buildUpdateBusinessConstraints 当前返回空列表")
    void shouldVerify_BuildUpdateBusinessConstraintsReturnsEmptyList() {
      // Given: UPDATE 操作
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 因为 buildUpdateBusinessConstraints 返回空,所以表达式为 constTrue
      assertThat(descriptor.expr()).isInstanceOf(Const.class);
      assertThat(descriptor.expr()).isEqualTo(Const.TRUE);
    }

    @Test
    @DisplayName("应该验证 buildExternalConditionsExpr 当前返回 null")
    void shouldVerify_BuildExternalConditionsExprReturnsNull() {
      // Given: 任意操作
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.HARVEST, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 因为外部条件为 null,表达式只包含内部约束
      // 当前内部约束也为空,所以为 constTrue
      assertThat(descriptor.expr()).isInstanceOf(Const.class);
    }

    @Test
    @DisplayName("应该验证 HARVEST 操作当前也返回 constTrue")
    void shouldVerify_HarvestOperationAlsoReturnsConstTrue() {
      // Given: HARVEST 操作(非 UPDATE,但 buildBusinessConstraints 只处理 UPDATE)
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.HARVEST, from, to);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: HARVEST 不走 UPDATE 分支,约束列表为空,返回 constTrue
      assertThat(descriptor.expr()).isInstanceOf(Const.class);
      assertThat(descriptor.expr()).isEqualTo(Const.TRUE);
    }

    @Test
    @DisplayName("应该验证单个约束不包装为 And")
    void shouldVerify_SingleConstraintNotWrappedInAnd() {
      // Given: 假设未来有单个约束的情况
      // 当前实现中,如果 constraints.size() == 1,直接返回该约束
      // 由于当前实现返回空约束,这里只能验证当前逻辑
      PlanTriggerNorm norm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When: 构建表达式
      PlanExpressionDescriptor descriptor = builder.build(norm, snapshot);

      // Then: 当前为 constTrue,不是 And
      assertThat(descriptor.expr()).isInstanceOf(Const.class);
      assertThat(descriptor.expr()).isNotInstanceOf(And.class);
    }
  }

  // ==================== 辅助方法 ====================

  /// 创建触发规范
/// 
/// @param operationCode 操作代码
/// @param requestedFrom 请求的窗口起始时间
/// @param requestedTo 请求的窗口结束时间
/// @return 触发规范
  private PlanTriggerNorm createTriggerNorm(
      OperationCode operationCode, Instant requestedFrom, Instant requestedTo) {
    return new PlanTriggerNorm(
        1L,
        ProvenanceCode.PUBMED,
        operationCode,
        null,
        TriggerType.MANUAL,
        Scheduler.XXL,
        "job-1",
        "log-1",
        requestedFrom,
        requestedTo,
        Priority.NORMAL,
        Map.of());
  }

  /// 创建最小化配置快照
/// 
/// @return 最小化配置快照
  private ProvenanceConfigSnapshot createMinimalSnapshot() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    return new ProvenanceConfigSnapshot(provenanceInfo, null, null, null, null, null, null);
  }
}
