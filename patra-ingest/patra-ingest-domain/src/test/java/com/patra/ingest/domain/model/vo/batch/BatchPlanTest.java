package com.patra.ingest.domain.model.vo.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BatchPlan} 的单元测试。
 *
 * <p>测试覆盖：
 *
 * <ul>
 *   <li>成功构造场景（有效字段、空列表、多批次、超限标志）
 *   <li>验证失败场景（null batches、负数 totalBatches）
 *   <li>工厂方法（empty、single）
 *   <li>业务方法（hasBatches）
 *   <li>Record 语义（equals、hashCode、toString、访问器）
 *   <li>不可变性验证
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("BatchPlan 批次规划结果值对象测试")
class BatchPlanTest {

  // ==================== 测试工具方法 ====================

  /** 创建一个测试用 Batch 实例 */
  private Batch createTestBatch(int batchNo) {
    return Batch.first("test query " + batchNo, JsonNodeFactory.instance.objectNode());
  }

  // ==================== 成功构造测试 ====================

  @Nested
  @DisplayName("成功构造场景")
  class SuccessfulConstruction {

    @Test
    @DisplayName("应该成功创建包含单个批次且不超限的计划")
    void shouldCreatePlanWithSingleBatchNotExceedingLimit() {
      // Given: 准备单个批次
      Batch batch = createTestBatch(1);
      List<Batch> batches = List.of(batch);

      // When: 创建批次计划
      BatchPlan plan = new BatchPlan(batches, 1, false);

      // Then: 验证所有字段
      assertThat(plan.batches()).hasSize(1).containsExactly(batch);
      assertThat(plan.totalBatches()).isEqualTo(1);
      assertThat(plan.exceedsLimit()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建空批次列表的计划")
    void shouldCreatePlanWithEmptyBatchList() {
      // Given: 空批次列表
      List<Batch> emptyBatches = List.of();

      // When: 创建批次计划
      BatchPlan plan = new BatchPlan(emptyBatches, 0, false);

      // Then: 验证空列表
      assertThat(plan.batches()).isEmpty();
      assertThat(plan.totalBatches()).isZero();
      assertThat(plan.exceedsLimit()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建包含多个批次的计划")
    void shouldCreatePlanWithMultipleBatches() {
      // Given: 多个批次
      List<Batch> batches =
          List.of(createTestBatch(1), createTestBatch(2), createTestBatch(3));

      // When: 创建批次计划
      BatchPlan plan = new BatchPlan(batches, 3, false);

      // Then: 验证批次列表
      assertThat(plan.batches()).hasSize(3);
      assertThat(plan.totalBatches()).isEqualTo(3);
      assertThat(plan.exceedsLimit()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建超出限制的计划")
    void shouldCreatePlanWithExceedsLimitFlag() {
      // Given: 超出限制的批次
      List<Batch> batches = List.of(createTestBatch(1), createTestBatch(2));

      // When: 创建超限计划
      BatchPlan plan = new BatchPlan(batches, 2, true);

      // Then: 验证超限标志
      assertThat(plan.batches()).hasSize(2);
      assertThat(plan.totalBatches()).isEqualTo(2);
      assertThat(plan.exceedsLimit()).isTrue();
    }

    @Test
    @DisplayName("应该允许 totalBatches 为零")
    void shouldAllowTotalBatchesZero() {
      // Given: 空批次和零总数
      List<Batch> emptyBatches = List.of();

      // When: 创建零批次计划
      BatchPlan plan = new BatchPlan(emptyBatches, 0, false);

      // Then: 验证零值
      assertThat(plan.totalBatches()).isZero();
    }
  }

  // ==================== 验证失败测试 ====================

  @Nested
  @DisplayName("验证失败场景")
  class ValidationFailures {

    @Test
    @DisplayName("当 batches 为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenBatchesIsNull() {
      // Given: null 批次列表
      List<Batch> nullBatches = null;

      // When & Then: 验证异常
      assertThatThrownBy(() -> new BatchPlan(nullBatches, 1, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("batches must not be null");
    }

    @Test
    @DisplayName("当 totalBatches 为负数时应该抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenTotalBatchesIsNegative() {
      // Given: 有效批次列表但负数总数
      List<Batch> batches = List.of(createTestBatch(1));

      // When & Then: 验证异常
      assertThatThrownBy(() -> new BatchPlan(batches, -1, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("totalBatches must not be negative");
    }

    @Test
    @DisplayName("当 totalBatches 为最小整数时应该抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenTotalBatchesIsMinValue() {
      // Given: 有效批次列表但最小整数
      List<Batch> batches = List.of(createTestBatch(1));

      // When & Then: 验证异常
      assertThatThrownBy(() -> new BatchPlan(batches, Integer.MIN_VALUE, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("totalBatches must not be negative");
    }
  }

  // ==================== 工厂方法测试 ====================

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethods {

    @Test
    @DisplayName("empty() 应该创建空批次计划")
    void emptyShouldCreateEmptyPlan() {
      // When: 调用 empty 工厂方法
      BatchPlan plan = BatchPlan.empty();

      // Then: 验证空计划
      assertThat(plan.batches()).isEmpty();
      assertThat(plan.totalBatches()).isZero();
      assertThat(plan.exceedsLimit()).isFalse();
      assertThat(plan.hasBatches()).isFalse();
    }

    @Test
    @DisplayName("single() 应该创建包含单个批次的计划")
    void singleShouldCreatePlanWithOneBatch() {
      // Given: 准备单个批次
      Batch batch = createTestBatch(1);

      // When: 调用 single 工厂方法
      BatchPlan plan = BatchPlan.single(batch);

      // Then: 验证单批次计划
      assertThat(plan.batches()).hasSize(1).containsExactly(batch);
      assertThat(plan.totalBatches()).isEqualTo(1);
      assertThat(plan.exceedsLimit()).isFalse();
      assertThat(plan.hasBatches()).isTrue();
    }

    @Test
    @DisplayName("empty() 和 single() 应该创建不同的实例")
    void factoryMethodsShouldCreateDistinctInstances() {
      // When: 多次调用工厂方法
      BatchPlan empty1 = BatchPlan.empty();
      BatchPlan empty2 = BatchPlan.empty();
      BatchPlan single1 = BatchPlan.single(createTestBatch(1));
      BatchPlan single2 = BatchPlan.single(createTestBatch(1));

      // Then: 验证不同实例（但值相等）
      assertThat(empty1).isNotSameAs(empty2).isEqualTo(empty2);
      assertThat(single1).isNotSameAs(single2);
    }
  }

  // ==================== 业务方法测试 ====================

  @Nested
  @DisplayName("业务方法")
  class BusinessMethods {

    @Test
    @DisplayName("hasBatches() 应该在包含批次时返回 true")
    void hasBatchesShouldReturnTrueWhenBatchesExist() {
      // Given: 包含批次的计划
      List<Batch> batches = List.of(createTestBatch(1));
      BatchPlan plan = new BatchPlan(batches, 1, false);

      // When & Then: 验证有批次
      assertThat(plan.hasBatches()).isTrue();
    }

    @Test
    @DisplayName("hasBatches() 应该在空列表时返回 false")
    void hasBatchesShouldReturnFalseWhenEmpty() {
      // Given: 空批次计划
      BatchPlan plan = BatchPlan.empty();

      // When & Then: 验证无批次
      assertThat(plan.hasBatches()).isFalse();
    }

    @Test
    @DisplayName("hasBatches() 应该在包含多个批次时返回 true")
    void hasBatchesShouldReturnTrueWithMultipleBatches() {
      // Given: 多批次计划
      List<Batch> batches = List.of(createTestBatch(1), createTestBatch(2));
      BatchPlan plan = new BatchPlan(batches, 2, false);

      // When & Then: 验证有批次
      assertThat(plan.hasBatches()).isTrue();
    }
  }

  // ==================== Record 语义测试 ====================

  @Nested
  @DisplayName("Record 语义")
  class RecordSemantics {

    @Test
    @DisplayName("equals() 应该对相同值返回 true")
    void equalsShouldReturnTrueForSameValues() {
      // Given: 两个相同值的计划
      Batch batch = createTestBatch(1);
      List<Batch> batches = List.of(batch);
      BatchPlan plan1 = new BatchPlan(batches, 1, false);
      BatchPlan plan2 = new BatchPlan(batches, 1, false);

      // When & Then: 验证相等性
      assertThat(plan1).isEqualTo(plan2);
      assertThat(plan1.equals(plan2)).isTrue();
    }

    @Test
    @DisplayName("equals() 应该对不同值返回 false")
    void equalsShouldReturnFalseForDifferentValues() {
      // Given: 不同值的计划
      BatchPlan plan1 = new BatchPlan(List.of(createTestBatch(1)), 1, false);
      BatchPlan plan2 = new BatchPlan(List.of(createTestBatch(2)), 1, false);
      BatchPlan plan3 = new BatchPlan(List.of(createTestBatch(1)), 2, false);
      BatchPlan plan4 = new BatchPlan(List.of(createTestBatch(1)), 1, true);

      // When & Then: 验证不相等
      assertThat(plan1).isNotEqualTo(plan2).isNotEqualTo(plan3).isNotEqualTo(plan4);
    }

    @Test
    @DisplayName("equals() 应该对 null 返回 false")
    void equalsShouldReturnFalseForNull() {
      // Given: 一个有效计划
      BatchPlan plan = BatchPlan.empty();

      // When & Then: 验证与 null 不相等
      assertThat(plan.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals() 应该对不同类型返回 false")
    void equalsShouldReturnFalseForDifferentType() {
      // Given: 一个有效计划和不同类型对象
      BatchPlan plan = BatchPlan.empty();
      Object other = new Object();

      // When & Then: 验证与不同类型不相等
      assertThat(plan.equals(other)).isFalse();
    }

    @Test
    @DisplayName("equals() 应该对自身返回 true")
    void equalsShouldReturnTrueForSelf() {
      // Given: 一个有效计划
      BatchPlan plan = BatchPlan.empty();

      // When & Then: 验证自反性
      assertThat(plan).isEqualTo(plan);
    }

    @Test
    @DisplayName("hashCode() 应该对相同值返回相同哈希码")
    void hashCodeShouldReturnSameValueForEqualObjects() {
      // Given: 两个相同值的计划
      Batch batch = createTestBatch(1);
      List<Batch> batches = List.of(batch);
      BatchPlan plan1 = new BatchPlan(batches, 1, false);
      BatchPlan plan2 = new BatchPlan(batches, 1, false);

      // When & Then: 验证哈希码一致性
      assertThat(plan1.hashCode()).isEqualTo(plan2.hashCode());
    }

    @Test
    @DisplayName("hashCode() 应该对不同值返回不同哈希码（通常情况）")
    void hashCodeShouldReturnDifferentValueForDifferentObjects() {
      // Given: 不同值的计划
      BatchPlan plan1 = new BatchPlan(List.of(createTestBatch(1)), 1, false);
      BatchPlan plan2 = new BatchPlan(List.of(createTestBatch(2)), 2, true);

      // When & Then: 验证哈希码不同（通常情况）
      assertThat(plan1.hashCode()).isNotEqualTo(plan2.hashCode());
    }

    @Test
    @DisplayName("toString() 应该包含所有字段信息")
    void toStringShouldContainAllFields() {
      // Given: 包含批次的计划
      Batch batch = createTestBatch(1);
      List<Batch> batches = List.of(batch);
      BatchPlan plan = new BatchPlan(batches, 1, true);

      // When: 调用 toString
      String result = plan.toString();

      // Then: 验证包含字段名称和值
      assertThat(result)
          .contains("BatchPlan")
          .contains("batches")
          .contains("totalBatches")
          .contains("exceedsLimit")
          .contains("1")
          .contains("true");
    }

    @Test
    @DisplayName("组件访问器应该返回正确的字段值")
    void componentAccessorsShouldReturnCorrectValues() {
      // Given: 创建计划
      Batch batch = createTestBatch(1);
      List<Batch> batches = List.of(batch);
      BatchPlan plan = new BatchPlan(batches, 5, true);

      // When & Then: 验证访问器
      assertThat(plan.batches()).isEqualTo(batches);
      assertThat(plan.totalBatches()).isEqualTo(5);
      assertThat(plan.exceedsLimit()).isTrue();
    }
  }

  // ==================== 不可变性测试 ====================

  @Nested
  @DisplayName("不可变性验证")
  class Immutability {

    @Test
    @DisplayName("batches 列表应该是不可变的")
    void batchesListShouldBeImmutable() {
      // Given: 创建计划
      Batch batch = createTestBatch(1);
      List<Batch> batches = List.of(batch);
      BatchPlan plan = new BatchPlan(batches, 1, false);

      // When & Then: 验证列表不可修改
      assertThatThrownBy(() -> plan.batches().add(createTestBatch(2)))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("外部修改可变列表不应影响 BatchPlan")
    void externalMutableListModificationShouldNotAffectPlan() {
      // Given: 使用 ArrayList 创建计划
      List<Batch> mutableList = new ArrayList<>();
      mutableList.add(createTestBatch(1));
      BatchPlan plan = new BatchPlan(List.copyOf(mutableList), 1, false);

      int originalSize = plan.batches().size();

      // When: 修改外部列表
      mutableList.add(createTestBatch(2));

      // Then: 计划内部列表不受影响
      assertThat(plan.batches()).hasSize(originalSize);
    }

    @Test
    @DisplayName("Record 实例应该是不可变的")
    void recordInstanceShouldBeImmutable() {
      // Given: 创建计划
      Batch batch = createTestBatch(1);
      List<Batch> batches = List.of(batch);
      BatchPlan plan = new BatchPlan(batches, 1, false);

      // When: 保存原始值
      List<Batch> originalBatches = plan.batches();
      int originalTotal = plan.totalBatches();
      boolean originalFlag = plan.exceedsLimit();

      // Then: 多次访问应返回相同值（验证不可变）
      assertThat(plan.batches()).isSameAs(originalBatches);
      assertThat(plan.totalBatches()).isEqualTo(originalTotal);
      assertThat(plan.exceedsLimit()).isEqualTo(originalFlag);
    }
  }

  // ==================== 边界条件测试 ====================

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditions {

    @Test
    @DisplayName("应该处理 totalBatches 为 Integer.MAX_VALUE")
    void shouldHandleMaxIntegerTotalBatches() {
      // Given: 使用最大整数值
      List<Batch> batches = List.of(createTestBatch(1));

      // When: 创建计划
      BatchPlan plan = new BatchPlan(batches, Integer.MAX_VALUE, true);

      // Then: 验证可正常创建
      assertThat(plan.totalBatches()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理大量批次")
    void shouldHandleLargeNumberOfBatches() {
      // Given: 创建大量批次
      List<Batch> batches =
          List.of(
              createTestBatch(1),
              createTestBatch(2),
              createTestBatch(3),
              createTestBatch(4),
              createTestBatch(5),
              createTestBatch(6),
              createTestBatch(7),
              createTestBatch(8),
              createTestBatch(9),
              createTestBatch(10));

      // When: 创建计划
      BatchPlan plan = new BatchPlan(batches, 10, false);

      // Then: 验证正确处理
      assertThat(plan.batches()).hasSize(10);
      assertThat(plan.totalBatches()).isEqualTo(10);
      assertThat(plan.hasBatches()).isTrue();
    }
  }

  // ==================== 工厂方法与构造器等价性测试 ====================

  @Nested
  @DisplayName("工厂方法与构造器等价性")
  class FactoryConstructorEquivalence {

    @Test
    @DisplayName("empty() 应该等价于构造器创建的空计划")
    void emptyShouldBeEquivalentToConstructor() {
      // Given: 使用工厂方法和构造器
      BatchPlan factoryPlan = BatchPlan.empty();
      BatchPlan constructorPlan = new BatchPlan(List.of(), 0, false);

      // When & Then: 验证等价性
      assertThat(factoryPlan).isEqualTo(constructorPlan);
    }

    @Test
    @DisplayName("single() 应该等价于构造器创建的单批次计划")
    void singleShouldBeEquivalentToConstructor() {
      // Given: 准备批次
      Batch batch = createTestBatch(1);

      // When: 使用工厂方法和构造器
      BatchPlan factoryPlan = BatchPlan.single(batch);
      BatchPlan constructorPlan = new BatchPlan(List.of(batch), 1, false);

      // Then: 验证等价性
      assertThat(factoryPlan).isEqualTo(constructorPlan);
    }
  }
}
