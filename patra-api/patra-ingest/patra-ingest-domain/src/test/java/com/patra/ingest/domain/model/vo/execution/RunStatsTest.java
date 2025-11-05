package com.patra.ingest.domain.model.vo.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RunStats 值对象单元测试")
class RunStatsTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用所有字段创建有效的运行统计")
    void shouldCreateValidRunStats() {
      // Given
      long fetched = 100L;
      long upserted = 90L;
      long failed = 10L;
      long pages = 5L;

      // When
      RunStats stats = new RunStats(fetched, upserted, failed, pages);

      // Then
      assertThat(stats.fetched()).isEqualTo(100L);
      assertThat(stats.upserted()).isEqualTo(90L);
      assertThat(stats.failed()).isEqualTo(10L);
      assertThat(stats.pages()).isEqualTo(5L);
    }

    @Test
    @DisplayName("应该允许创建零值统计")
    void shouldAllowZeroValues() {
      // When
      RunStats stats = new RunStats(0, 0, 0, 0);

      // Then
      assertThat(stats.fetched()).isZero();
      assertThat(stats.upserted()).isZero();
      assertThat(stats.failed()).isZero();
      assertThat(stats.pages()).isZero();
    }

    @Test
    @DisplayName("应该允许创建部分零值统计")
    void shouldAllowPartialZeroValues() {
      // When
      RunStats stats = new RunStats(100, 0, 0, 1);

      // Then
      assertThat(stats.fetched()).isEqualTo(100L);
      assertThat(stats.upserted()).isZero();
      assertThat(stats.failed()).isZero();
      assertThat(stats.pages()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该允许创建负数值（虽然不推荐）")
    void shouldAllowNegativeValues() {
      // When
      RunStats stats = new RunStats(-10, -5, -1, -2);

      // Then
      assertThat(stats.fetched()).isEqualTo(-10L);
      assertThat(stats.upserted()).isEqualTo(-5L);
      assertThat(stats.failed()).isEqualTo(-1L);
      assertThat(stats.pages()).isEqualTo(-2L);
    }

    @Test
    @DisplayName("应该允许创建最大长整型值")
    void shouldAllowMaxLongValues() {
      // When
      RunStats stats =
          new RunStats(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);

      // Then
      assertThat(stats.fetched()).isEqualTo(Long.MAX_VALUE);
      assertThat(stats.upserted()).isEqualTo(Long.MAX_VALUE);
      assertThat(stats.failed()).isEqualTo(Long.MAX_VALUE);
      assertThat(stats.pages()).isEqualTo(Long.MAX_VALUE);
    }
  }

  @Nested
  @DisplayName("静态工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("empty() 应该返回所有字段为零的统计对象")
    void emptyShouldReturnAllZeroStats() {
      // When
      RunStats empty = RunStats.empty();

      // Then
      assertThat(empty.fetched()).isZero();
      assertThat(empty.upserted()).isZero();
      assertThat(empty.failed()).isZero();
      assertThat(empty.pages()).isZero();
    }

    @Test
    @DisplayName("empty() 应该返回等同于零值构造的对象")
    void emptyShouldReturnEquivalentToZeroConstructor() {
      // When
      RunStats empty = RunStats.empty();
      RunStats zero = new RunStats(0, 0, 0, 0);

      // Then
      assertThat(empty).isEqualTo(zero);
    }
  }

  @Nested
  @DisplayName("add() 方法测试")
  class AddMethodTests {

    @Test
    @DisplayName("应该正确合并两个统计对象")
    void shouldCorrectlyMergeTwoStats() {
      // Given
      RunStats stats1 = new RunStats(100, 90, 10, 5);
      RunStats stats2 = new RunStats(50, 45, 5, 3);

      // When
      RunStats result = stats1.add(stats2);

      // Then
      assertThat(result.fetched()).isEqualTo(150L);
      assertThat(result.upserted()).isEqualTo(135L);
      assertThat(result.failed()).isEqualTo(15L);
      assertThat(result.pages()).isEqualTo(8L);
    }

    @Test
    @DisplayName("与空统计相加应该返回原值")
    void addingEmptyStatsShouldReturnOriginal() {
      // Given
      RunStats stats = new RunStats(100, 90, 10, 5);
      RunStats empty = RunStats.empty();

      // When
      RunStats result = stats.add(empty);

      // Then
      assertThat(result).isEqualTo(stats);
    }

    @Test
    @DisplayName("空统计与其他统计相加应该返回其他统计的值")
    void emptyAddingStatsShouldReturnOtherStats() {
      // Given
      RunStats empty = RunStats.empty();
      RunStats stats = new RunStats(100, 90, 10, 5);

      // When
      RunStats result = empty.add(stats);

      // Then
      assertThat(result).isEqualTo(stats);
    }

    @Test
    @DisplayName("应该支持链式合并多个统计")
    void shouldSupportChainingMultipleAdds() {
      // Given
      RunStats stats1 = new RunStats(10, 9, 1, 1);
      RunStats stats2 = new RunStats(20, 18, 2, 1);
      RunStats stats3 = new RunStats(30, 27, 3, 1);

      // When
      RunStats result = stats1.add(stats2).add(stats3);

      // Then
      assertThat(result.fetched()).isEqualTo(60L);
      assertThat(result.upserted()).isEqualTo(54L);
      assertThat(result.failed()).isEqualTo(6L);
      assertThat(result.pages()).isEqualTo(3L);
    }

    @Test
    @DisplayName("自己与自己相加应该返回翻倍的值")
    void addingSelfShouldDoubleValues() {
      // Given
      RunStats stats = new RunStats(10, 8, 2, 1);

      // When
      RunStats result = stats.add(stats);

      // Then
      assertThat(result.fetched()).isEqualTo(20L);
      assertThat(result.upserted()).isEqualTo(16L);
      assertThat(result.failed()).isEqualTo(4L);
      assertThat(result.pages()).isEqualTo(2L);
    }

    @Test
    @DisplayName("add() 应该是不可变操作")
    void addShouldBeImmutable() {
      // Given
      RunStats original = new RunStats(100, 90, 10, 5);
      RunStats delta = new RunStats(50, 45, 5, 3);

      // When
      RunStats result = original.add(delta);

      // Then
      assertThat(original.fetched()).isEqualTo(100L); // 原始对象未改变
      assertThat(result.fetched()).isEqualTo(150L); // 新对象包含合并结果
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同字段的实例应该相等")
    void instancesWithSameFieldsShouldBeEqual() {
      // Given
      RunStats stats1 = new RunStats(100, 90, 10, 5);
      RunStats stats2 = new RunStats(100, 90, 10, 5);

      // Then
      assertThat(stats1).isEqualTo(stats2);
      assertThat(stats1.hashCode()).isEqualTo(stats2.hashCode());
    }

    @Test
    @DisplayName("不同字段的实例应该不相等")
    void instancesWithDifferentFieldsShouldNotBeEqual() {
      // Given
      RunStats stats1 = new RunStats(100, 90, 10, 5);
      RunStats stats2 = new RunStats(100, 90, 10, 6); // pages 不同

      // Then
      assertThat(stats1).isNotEqualTo(stats2);
    }

    @Test
    @DisplayName("toString() 应该包含所有字段信息")
    void toStringShouldContainAllFields() {
      // Given
      RunStats stats = new RunStats(100, 90, 10, 5);

      // When
      String result = stats.toString();

      // Then
      assertThat(result)
          .contains("RunStats")
          .contains("fetched=100")
          .contains("upserted=90")
          .contains("failed=10")
          .contains("pages=5");
    }

    @Test
    @DisplayName("应该支持作为 Map 的键")
    void shouldWorkAsMapKey() {
      // Given
      var map = new java.util.HashMap<RunStats, String>();
      RunStats key1 = new RunStats(100, 90, 10, 5);
      RunStats key2 = new RunStats(100, 90, 10, 5);

      // When
      map.put(key1, "value1");

      // Then
      assertThat(map.get(key2)).isEqualTo("value1"); // 相同值可以检索
      assertThat(map).containsKey(key1);
      assertThat(map).containsKey(key2);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理只有获取数据而没有更新插入的场景")
    void shouldHandleOnlyFetchedNoUpserted() {
      // Given
      RunStats stats = new RunStats(1000, 0, 0, 10);

      // Then
      assertThat(stats.fetched()).isEqualTo(1000L);
      assertThat(stats.upserted()).isZero();
    }

    @Test
    @DisplayName("应该处理全部失败的场景")
    void shouldHandleAllFailed() {
      // Given
      RunStats stats = new RunStats(100, 0, 100, 5);

      // Then
      assertThat(stats.fetched()).isEqualTo(100L);
      assertThat(stats.failed()).isEqualTo(100L);
      assertThat(stats.upserted()).isZero();
    }

    @Test
    @DisplayName("应该处理全部成功的场景")
    void shouldHandleAllSucceeded() {
      // Given
      RunStats stats = new RunStats(100, 100, 0, 5);

      // Then
      assertThat(stats.fetched()).isEqualTo(100L);
      assertThat(stats.upserted()).isEqualTo(100L);
      assertThat(stats.failed()).isZero();
    }

    @Test
    @DisplayName("应该处理单页/批次的场景")
    void shouldHandleSinglePage() {
      // Given
      RunStats stats = new RunStats(10, 10, 0, 1);

      // Then
      assertThat(stats.pages()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该处理大量页数的场景")
    void shouldHandleManyPages() {
      // Given
      RunStats stats = new RunStats(100000, 99000, 1000, 10000);

      // Then
      assertThat(stats.pages()).isEqualTo(10000L);
    }
  }
}
