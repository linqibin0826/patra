package com.patra.ingest.domain.model.vo.batch;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link BatchStats} 的单元测试。
///
/// 测试覆盖:
///
/// - 构造方法 (recordCount >= 0，无验证逻辑)
///   - 工厂方法 (of)
///   - Record 语义 (equals, hashCode, toString, 组件访问器)
///   - 不变性保证
///   - 边界条件测试
///   - 业务场景测试
///
/// @author Patra Team
@DisplayName("BatchStats 单元测试")
class BatchStatsTest {

  @Nested
  @DisplayName("构造方法")
  class ConstructorTests {

    @Test
    @DisplayName("应该成功创建 - recordCount = 0")
    void shouldCreateBatchStatsWithZeroRecordCount() {
      // When: 创建 recordCount = 0 的统计对象
      BatchStats stats = new BatchStats(0);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isZero();
    }

    @Test
    @DisplayName("应该成功创建 - recordCount > 0")
    void shouldCreateBatchStatsWithPositiveRecordCount() {
      // When: 创建 recordCount > 0 的统计对象
      BatchStats stats = new BatchStats(100);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("应该接受 recordCount = 1 (最小正数)")
    void shouldAcceptMinimumPositiveRecordCount() {
      // When: recordCount = 1
      BatchStats stats = new BatchStats(1);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受极大的 recordCount")
    void shouldAcceptVeryLargeRecordCount() {
      // When: recordCount = Integer.MAX_VALUE
      BatchStats stats = new BatchStats(Integer.MAX_VALUE);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受负数 recordCount (调用方保证，无验证)")
    void shouldAcceptNegativeRecordCount() {
      // Given: 文档说明调用方保证非负，此类不进行验证

      // When: 创建 recordCount < 0 的统计对象
      BatchStats stats = new BatchStats(-1);

      // Then: 应该成功创建 (无验证逻辑)
      assertThat(stats.recordCount()).isEqualTo(-1);
    }

    @Test
    @DisplayName("业务场景 - 空批次")
    void shouldCreateStatsForEmptyBatch() {
      // Given: 批次处理完成，但没有处理任何记录
      int recordCount = 0;

      // When: 创建统计对象
      BatchStats stats = new BatchStats(recordCount);

      // Then: 应该正确设置
      assertThat(stats.recordCount()).isZero();
    }

    @Test
    @DisplayName("业务场景 - 正常批次")
    void shouldCreateStatsForNormalBatch() {
      // Given: 批次处理完成，处理了 1000 条记录
      int recordCount = 1000;

      // When: 创建统计对象
      BatchStats stats = new BatchStats(recordCount);

      // Then: 应该正确设置
      assertThat(stats.recordCount()).isEqualTo(1000);
    }
  }

  @Nested
  @DisplayName("工厂方法: of()")
  class FactoryMethodTests {

    @Test
    @DisplayName("应该创建批次统计对象 - recordCount = 0")
    void shouldCreateBatchStatsWithZeroRecordCount() {
      // When: 调用 of() 工厂方法，recordCount = 0
      BatchStats stats = BatchStats.of(0);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isZero();
    }

    @Test
    @DisplayName("应该创建批次统计对象 - recordCount > 0")
    void shouldCreateBatchStatsWithPositiveRecordCount() {
      // When: 调用 of() 工厂方法，recordCount > 0
      BatchStats stats = BatchStats.of(500);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("应该接受 recordCount = 1 (最小正数)")
    void shouldAcceptMinimumPositiveRecordCount() {
      // When: recordCount = 1
      BatchStats stats = BatchStats.of(1);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受极大的 recordCount")
    void shouldAcceptVeryLargeRecordCount() {
      // When: recordCount = Integer.MAX_VALUE
      BatchStats stats = BatchStats.of(Integer.MAX_VALUE);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受负数 recordCount (调用方保证，无验证)")
    void shouldAcceptNegativeRecordCount() {
      // Given: 文档说明调用方保证非负，此类不进行验证

      // When: 调用 of()，recordCount < 0
      BatchStats stats = BatchStats.of(-100);

      // Then: 应该成功创建 (无验证逻辑)
      assertThat(stats.recordCount()).isEqualTo(-100);
    }

    @Test
    @DisplayName("业务场景 - 批次处理统计")
    void shouldCreateStatsForBatchProcessing() {
      // Given: 批次处理完成，记录了处理记录数
      int totalRecords = 2500;

      // When: 使用工厂方法创建统计对象
      BatchStats stats = BatchStats.of(totalRecords);

      // Then: 应该正确设置
      assertThat(stats.recordCount()).isEqualTo(2500);
    }

    @Test
    @DisplayName("工厂方法和构造方法应该等价")
    void shouldBeEquivalentToConstructor() {
      // Given: 相同的 recordCount
      int recordCount = 100;

      // When: 分别使用工厂方法和构造方法创建
      BatchStats statsFromFactory = BatchStats.of(recordCount);
      BatchStats statsFromConstructor = new BatchStats(recordCount);

      // Then: 应该相等
      assertThat(statsFromFactory).isEqualTo(statsFromConstructor);
    }
  }

  @Nested
  @DisplayName("Record 语义: equals() 和 hashCode()")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("equals() - 相同值应相等")
    void shouldBeEqualForSameValues() {
      // Given: 两个相同值的 BatchStats
      BatchStats stats1 = BatchStats.of(100);
      BatchStats stats2 = BatchStats.of(100);

      // When & Then: 应该相等
      assertThat(stats1).isEqualTo(stats2).hasSameHashCodeAs(stats2);
    }

    @Test
    @DisplayName("equals() - 不同 recordCount 应不相等")
    void shouldNotBeEqualForDifferentRecordCount() {
      // Given: recordCount 不同的 BatchStats
      BatchStats stats1 = BatchStats.of(100);
      BatchStats stats2 = BatchStats.of(200);

      // When & Then: 应该不相等
      assertThat(stats1).isNotEqualTo(stats2);
    }

    @Test
    @DisplayName("equals() - 与自身比较应返回 true")
    void shouldBeEqualToItself() {
      // Given: 一个 BatchStats
      BatchStats stats = BatchStats.of(100);

      // When & Then: 与自身比较应相等
      assertThat(stats).isEqualTo(stats);
    }

    @Test
    @DisplayName("equals() - 与 null 比较应返回 false")
    void shouldNotBeEqualToNull() {
      // Given: 一个 BatchStats
      BatchStats stats = BatchStats.of(100);

      // When & Then: 与 null 比较应不相等
      assertThat(stats).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() - 与不同类型比较应返回 false")
    void shouldNotBeEqualToDifferentType() {
      // Given: 一个 BatchStats 和一个 String
      BatchStats stats = BatchStats.of(100);
      String other = "not a batch stats";

      // When & Then: 与不同类型比较应不相等
      assertThat(stats).isNotEqualTo(other);
    }

    @Test
    @DisplayName("hashCode() - 相同值应有相同的哈希码")
    void shouldHaveSameHashCodeForSameValues() {
      // Given: 两个相同值的 BatchStats
      BatchStats stats1 = BatchStats.of(100);
      BatchStats stats2 = BatchStats.of(100);

      // When & Then: 哈希码应相同
      assertThat(stats1.hashCode()).isEqualTo(stats2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 不同值通常应有不同的哈希码")
    void shouldHaveDifferentHashCodeForDifferentValues() {
      // Given: 两个不同值的 BatchStats
      BatchStats stats1 = BatchStats.of(100);
      BatchStats stats2 = BatchStats.of(200);

      // When & Then: 哈希码通常不同 (不是绝对保证，但概率很高)
      assertThat(stats1.hashCode()).isNotEqualTo(stats2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 多次调用应返回相同值")
    void shouldHaveConsistentHashCode() {
      // Given: 一个 BatchStats
      BatchStats stats = BatchStats.of(100);

      // When: 多次调用 hashCode()
      int hashCode1 = stats.hashCode();
      int hashCode2 = stats.hashCode();
      int hashCode3 = stats.hashCode();

      // Then: 应返回相同值
      assertThat(hashCode1).isEqualTo(hashCode2).isEqualTo(hashCode3);
    }

    @Test
    @DisplayName("应该在 HashSet 中正确工作 - 验证 equals 和 hashCode 契约")
    void shouldWorkCorrectlyInHashSet() {
      // Given: 多个 BatchStats
      BatchStats stats1 = BatchStats.of(100);
      BatchStats stats2 = BatchStats.of(100); // 相同值
      BatchStats stats3 = BatchStats.of(200); // 不同值

      // When: 添加到 HashSet
      Set<BatchStats> set = new HashSet<>();
      set.add(stats1);
      set.add(stats2); // 应该被去重
      set.add(stats3);

      // Then: Set 应该只包含 2 个不同的值
      assertThat(set).hasSize(2).contains(stats1, stats3);
    }
  }

  @Nested
  @DisplayName("Record 语义: toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() 应包含所有字段信息")
    void shouldIncludeAllFieldsInToString() {
      // Given: 一个 BatchStats
      BatchStats stats = BatchStats.of(500);

      // When: 调用 toString()
      String resultString = stats.toString();

      // Then: 应包含所有字段名和值
      assertThat(resultString).contains("BatchStats").contains("recordCount=500");
    }

    @Test
    @DisplayName("toString() 应正确显示 recordCount = 0")
    void shouldShowZeroRecordCountInToString() {
      // Given: recordCount = 0 的 BatchStats
      BatchStats stats = BatchStats.of(0);

      // When: 调用 toString()
      String resultString = stats.toString();

      // Then: 应显示 recordCount=0
      assertThat(resultString).contains("recordCount=0");
    }

    @Test
    @DisplayName("toString() 应正确显示负数 recordCount")
    void shouldShowNegativeRecordCountInToString() {
      // Given: recordCount < 0 的 BatchStats (虽然调用方应保证非负)
      BatchStats stats = BatchStats.of(-100);

      // When: 调用 toString()
      String resultString = stats.toString();

      // Then: 应显示 recordCount=-100
      assertThat(resultString).contains("recordCount=-100");
    }
  }

  @Nested
  @DisplayName("Record 语义: 组件访问器")
  class ComponentAccessorTests {

    @Test
    @DisplayName("recordCount() 应返回记录数")
    void shouldReturnRecordCount() {
      // Given: 一个 BatchStats
      BatchStats stats = BatchStats.of(1000);

      // When: 调用 recordCount()
      int recordCount = stats.recordCount();

      // Then: 应返回正确的值
      assertThat(recordCount).isEqualTo(1000);
    }

    @Test
    @DisplayName("组件访问器应返回不可变的值")
    void shouldReturnImmutableValue() {
      // Given: 一个 BatchStats
      BatchStats stats = BatchStats.of(100);

      // When: 多次调用组件访问器
      int count1 = stats.recordCount();
      int count2 = stats.recordCount();

      // Then: 应返回相同的值 (基本类型是不可变的)
      assertThat(count1).isEqualTo(count2);
    }
  }

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("类应该是 final - 防止子类破坏不变性")
    void shouldBeFinalClass() {
      // When & Then: BatchStats 应该是 final 类 (Record 自动 final)
      assertThat(BatchStats.class).isFinal();
    }

    @Test
    @DisplayName("字段应该是 final - 确保不可变")
    void shouldHaveFinalFields() throws NoSuchFieldException {
      // When & Then: 所有字段应该是 final (Record 自动 final)
      assertThat(BatchStats.class.getDeclaredField("recordCount"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("Record 应该是深度不可变的 (recordCount 是基本类型)")
    void shouldBeDeeplyImmutable() {
      // Given: 一个 BatchStats
      BatchStats stats = BatchStats.of(100);

      // When: 获取字段值
      int recordCount = stats.recordCount();

      // Then: 基本类型字段是不可变的 (int 是值类型)
      assertThat(recordCount).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该接受 recordCount = 0 (最小非负值)")
    void shouldAcceptMinimumNonNegativeRecordCount() {
      // When: recordCount = 0
      BatchStats stats = BatchStats.of(0);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isZero();
    }

    @Test
    @DisplayName("应该接受 recordCount = 1 (最小正数)")
    void shouldAcceptMinimumPositiveRecordCount() {
      // When: recordCount = 1
      BatchStats stats = BatchStats.of(1);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受极大的 recordCount")
    void shouldAcceptVeryLargeRecordCount() {
      // When: recordCount = Integer.MAX_VALUE
      BatchStats stats = BatchStats.of(Integer.MAX_VALUE);

      // Then: 应该成功创建
      assertThat(stats.recordCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受负数 recordCount (调用方保证，无验证)")
    void shouldAcceptNegativeRecordCount() {
      // Given: 文档说明调用方保证非负，此类不进行验证

      // When: recordCount < 0
      BatchStats stats1 = BatchStats.of(-1);
      BatchStats stats2 = BatchStats.of(-100);
      BatchStats stats3 = BatchStats.of(Integer.MIN_VALUE);

      // Then: 应该成功创建 (无验证逻辑)
      assertThat(stats1.recordCount()).isEqualTo(-1);
      assertThat(stats2.recordCount()).isEqualTo(-100);
      assertThat(stats3.recordCount()).isEqualTo(Integer.MIN_VALUE);
    }
  }

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenarioTests {

    @Test
    @DisplayName("业务场景 - 空批次统计")
    void shouldCreateStatsForEmptyBatch() {
      // Given: 批次处理完成，但没有处理任何记录
      int recordCount = 0;

      // When: 创建统计对象
      BatchStats stats = BatchStats.of(recordCount);

      // Then: 应该正确设置
      assertThat(stats.recordCount()).isZero();
    }

    @Test
    @DisplayName("业务场景 - 小批次统计")
    void shouldCreateStatsForSmallBatch() {
      // Given: 小批次处理完成，处理了少量记录
      int recordCount = 10;

      // When: 创建统计对象
      BatchStats stats = BatchStats.of(recordCount);

      // Then: 应该正确设置
      assertThat(stats.recordCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("业务场景 - 标准批次统计")
    void shouldCreateStatsForStandardBatch() {
      // Given: 标准批次处理完成，处理了 1000 条记录
      int recordCount = 1000;

      // When: 创建统计对象
      BatchStats stats = BatchStats.of(recordCount);

      // Then: 应该正确设置
      assertThat(stats.recordCount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("业务场景 - 大批次统计")
    void shouldCreateStatsForLargeBatch() {
      // Given: 大批次处理完成，处理了大量记录
      int recordCount = 100_000;

      // When: 创建统计对象
      BatchStats stats = BatchStats.of(recordCount);

      // Then: 应该正确设置
      assertThat(stats.recordCount()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("业务场景 - 批次统计汇总")
    void shouldAggregateBatchStats() {
      // Given: 多个批次统计
      BatchStats stats1 = BatchStats.of(1000);
      BatchStats stats2 = BatchStats.of(1500);
      BatchStats stats3 = BatchStats.of(800);

      // When: 汇总批次统计
      int totalRecords = stats1.recordCount() + stats2.recordCount() + stats3.recordCount();

      // Then: 应该正确汇总
      assertThat(totalRecords).isEqualTo(3300);
    }

    @Test
    @DisplayName("业务场景 - 批次统计去重")
    void shouldDeduplicateBatchStats() {
      // Given: 批量生成批次统计
      Set<BatchStats> statsSet = new HashSet<>();
      statsSet.add(BatchStats.of(100));
      statsSet.add(BatchStats.of(100)); // 重复
      statsSet.add(BatchStats.of(200));

      // Then: Set 应该自动去重
      assertThat(statsSet).hasSize(2);
    }

    @Test
    @DisplayName("业务场景 - 比较批次统计")
    void shouldCompareBatchStats() {
      // Given: 两个批次统计
      BatchStats stats1 = BatchStats.of(1000);
      BatchStats stats2 = BatchStats.of(500);

      // When: 比较记录数
      boolean isStats1Larger = stats1.recordCount() > stats2.recordCount();

      // Then: 应该正确比较
      assertThat(isStats1Larger).isTrue();
    }

    @Test
    @DisplayName("业务场景 - 计算批次处理进度")
    void shouldCalculateBatchProgress() {
      // Given: 批次统计信息
      BatchStats currentStats = BatchStats.of(500);
      int totalRecords = 1000;

      // When: 计算进度百分比
      double progress = (double) currentStats.recordCount() / totalRecords * 100;

      // Then: 应该正确计算
      assertThat(progress).isEqualTo(50.0);
    }
  }
}
