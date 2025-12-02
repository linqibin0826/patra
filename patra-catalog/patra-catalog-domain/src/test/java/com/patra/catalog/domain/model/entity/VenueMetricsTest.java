package com.patra.catalog.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueMetrics 实体单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 覆盖工厂方法、验证规则、计算方法和 equals/hashCode
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueMetrics 单元测试")
@Timeout(2)
class VenueMetricsTest {

  // ========== 测试数据 ==========

  private static final int VALID_YEAR = 2024;
  private static final int WORKS_COUNT = 1500;
  private static final int CITED_BY_COUNT = 25000;
  private static final int OA_WORKS_COUNT = 800;

  @Nested
  @DisplayName("create() 工厂方法测试")
  class CreateTests {

    @Test
    @DisplayName("应该正确创建年度指标（含 OA 作品数）")
    void shouldCreateMetricsWithOaWorksCount() {
      // When
      VenueMetrics metrics =
          VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, OA_WORKS_COUNT);

      // Then
      assertThat(metrics.getId()).isNull(); // 新建时无 ID
      assertThat(metrics.getYear()).isEqualTo(VALID_YEAR);
      assertThat(metrics.getWorksCount()).isEqualTo(WORKS_COUNT);
      assertThat(metrics.getCitedByCount()).isEqualTo(CITED_BY_COUNT);
      assertThat(metrics.getOaWorksCount()).isEqualTo(OA_WORKS_COUNT);
    }

    @Test
    @DisplayName("应该正确创建年度指标（不含 OA 作品数）")
    void shouldCreateMetricsWithoutOaWorksCount() {
      // When
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);

      // Then
      assertThat(metrics.getOaWorksCount()).isNull();
      assertThat(metrics.hasOaWorksCount()).isFalse();
    }

    @Test
    @DisplayName("应该正确创建空年度指标")
    void shouldCreateEmptyMetrics() {
      // When
      VenueMetrics metrics = VenueMetrics.empty(VALID_YEAR);

      // Then
      assertThat(metrics.getYear()).isEqualTo(VALID_YEAR);
      assertThat(metrics.getWorksCount()).isZero();
      assertThat(metrics.getCitedByCount()).isZero();
      assertThat(metrics.getOaWorksCount()).isNull();
    }
  }

  @Nested
  @DisplayName("年份验证测试")
  class YearValidationTests {

    @Test
    @DisplayName("年份在有效范围内应该通过")
    void shouldAcceptValidYearRange() {
      // 边界值测试
      assertThat(VenueMetrics.create(1900, 100, 500).getYear()).isEqualTo(1900);
      assertThat(VenueMetrics.create(2100, 100, 500).getYear()).isEqualTo(2100);
      assertThat(VenueMetrics.create(2024, 100, 500).getYear()).isEqualTo(2024);
    }

    @Test
    @DisplayName("年份小于 1900 应该抛出异常")
    void shouldRejectYearBefore1900() {
      assertThatThrownBy(() -> VenueMetrics.create(1899, 100, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("年份必须在 1900-2100 之间");
    }

    @Test
    @DisplayName("年份大于 2100 应该抛出异常")
    void shouldRejectYearAfter2100() {
      assertThatThrownBy(() -> VenueMetrics.create(2101, 100, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("年份必须在 1900-2100 之间");
    }
  }

  @Nested
  @DisplayName("作品数验证测试")
  class WorksCountValidationTests {

    @Test
    @DisplayName("作品数可以为 0")
    void shouldAcceptZeroWorksCount() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 0, 0);
      assertThat(metrics.getWorksCount()).isZero();
    }

    @Test
    @DisplayName("作品数为负数应该抛出异常")
    void shouldRejectNegativeWorksCount() {
      assertThatThrownBy(() -> VenueMetrics.create(VALID_YEAR, -1, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("发表作品数不能为负数");
    }

    @Test
    @DisplayName("被引次数为负数应该抛出异常")
    void shouldRejectNegativeCitedByCount() {
      assertThatThrownBy(() -> VenueMetrics.create(VALID_YEAR, 100, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("被引用次数不能为负数");
    }
  }

  @Nested
  @DisplayName("OA 作品数验证测试")
  class OaWorksCountValidationTests {

    @Test
    @DisplayName("OA 作品数可以为 null")
    void shouldAcceptNullOaWorksCount() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, null);
      assertThat(metrics.getOaWorksCount()).isNull();
    }

    @Test
    @DisplayName("OA 作品数可以为 0")
    void shouldAcceptZeroOaWorksCount() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, 0);
      assertThat(metrics.getOaWorksCount()).isZero();
    }

    @Test
    @DisplayName("OA 作品数为负数应该抛出异常")
    void shouldRejectNegativeOaWorksCount() {
      assertThatThrownBy(() -> VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OA 作品数不能为负数");
    }

    @Test
    @DisplayName("OA 作品数超过总作品数应该抛出异常")
    void shouldRejectOaWorksCountExceedingTotal() {
      assertThatThrownBy(() -> VenueMetrics.create(VALID_YEAR, 100, CITED_BY_COUNT, 150))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OA 作品数不能超过总作品数");
    }

    @Test
    @DisplayName("OA 作品数等于总作品数应该通过")
    void shouldAcceptOaWorksCountEqualToTotal() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 100, CITED_BY_COUNT, 100);
      assertThat(metrics.getOaWorksCount()).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("restore() 方法测试")
  class RestoreTests {

    @Test
    @DisplayName("应该正确从持久化状态重建实体")
    void shouldRestoreFromPersistedState() {
      // Given
      Long id = 123L;

      // When
      VenueMetrics metrics =
          VenueMetrics.restore(id, VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, OA_WORKS_COUNT);

      // Then
      assertThat(metrics.getId()).isEqualTo(id);
      assertThat(metrics.getYear()).isEqualTo(VALID_YEAR);
      assertThat(metrics.getWorksCount()).isEqualTo(WORKS_COUNT);
      assertThat(metrics.getCitedByCount()).isEqualTo(CITED_BY_COUNT);
      assertThat(metrics.getOaWorksCount()).isEqualTo(OA_WORKS_COUNT);
    }
  }

  @Nested
  @DisplayName("业务方法测试")
  class BusinessMethodTests {

    @Test
    @DisplayName("assignId() 应该设置 ID")
    void assignIdShouldSetId() {
      // Given
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);
      assertThat(metrics.getId()).isNull();

      // When
      metrics.assignId(456L);

      // Then
      assertThat(metrics.getId()).isEqualTo(456L);
    }

    @Test
    @DisplayName("updateCounts() 应该更新计数")
    void updateCountsShouldUpdateValues() {
      // Given
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 100, 500);

      // When
      metrics.updateCounts(200, 1000);

      // Then
      assertThat(metrics.getWorksCount()).isEqualTo(200);
      assertThat(metrics.getCitedByCount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("updateCounts() 验证负数")
    void updateCountsShouldValidateNegativeValues() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 100, 500);

      assertThatThrownBy(() -> metrics.updateCounts(-1, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("发表作品数不能为负数");

      assertThatThrownBy(() -> metrics.updateCounts(100, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("被引用次数不能为负数");
    }

    @Test
    @DisplayName("withOaWorksCount() 应该设置 OA 作品数")
    void withOaWorksCountShouldSetValue() {
      // Given
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);

      // When
      VenueMetrics result = metrics.withOaWorksCount(500);

      // Then
      assertThat(result).isSameAs(metrics); // 链式调用
      assertThat(metrics.getOaWorksCount()).isEqualTo(500);
      assertThat(metrics.hasOaWorksCount()).isTrue();
    }

    @Test
    @DisplayName("withOaWorksCount() 可以设置为 null")
    void withOaWorksCountCanSetNull() {
      // Given
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, 500);

      // When
      metrics.withOaWorksCount(null);

      // Then
      assertThat(metrics.getOaWorksCount()).isNull();
      assertThat(metrics.hasOaWorksCount()).isFalse();
    }

    @Test
    @DisplayName("withOaWorksCount() 验证约束")
    void withOaWorksCountShouldValidate() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 100, CITED_BY_COUNT);

      assertThatThrownBy(() -> metrics.withOaWorksCount(-1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OA 作品数不能为负数");

      assertThatThrownBy(() -> metrics.withOaWorksCount(150))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OA 作品数不能超过总作品数");
    }
  }

  @Nested
  @DisplayName("计算方法测试")
  class CalculationMethodTests {

    @Test
    @DisplayName("getAverageCitations() 应该正确计算平均被引次数")
    void getAverageCitationsShouldCalculateCorrectly() {
      // Given - 1500 篇作品，25000 次引用
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 1500, 25000);

      // When
      BigDecimal average = metrics.getAverageCitations();

      // Then - 25000 / 1500 = 16.67
      assertThat(average).isEqualByComparingTo("16.67");
    }

    @Test
    @DisplayName("getAverageCitations() 作品数为 0 时返回 0")
    void getAverageCitationsShouldReturnZeroWhenNoWorks() {
      // Given
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 0, 0);

      // When
      BigDecimal average = metrics.getAverageCitations();

      // Then
      assertThat(average).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getOaRatio() 应该正确计算 OA 比例")
    void getOaRatioShouldCalculateCorrectly() {
      // Given - 1500 篇作品，800 篇 OA
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 1500, CITED_BY_COUNT, 800);

      // When
      BigDecimal ratio = metrics.getOaRatio();

      // Then - 800 / 1500 = 0.5333
      assertThat(ratio).isEqualByComparingTo("0.5333");
    }

    @Test
    @DisplayName("getOaRatio() 作品数为 0 时返回 null")
    void getOaRatioShouldReturnNullWhenNoWorks() {
      // Given
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 0, 0, 0);

      // When
      BigDecimal ratio = metrics.getOaRatio();

      // Then
      assertThat(ratio).isNull();
    }

    @Test
    @DisplayName("getOaRatio() 无 OA 数据时返回 null")
    void getOaRatioShouldReturnNullWhenNoOaData() {
      // Given
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);

      // When
      BigDecimal ratio = metrics.getOaRatio();

      // Then
      assertThat(ratio).isNull();
    }

    @Test
    @DisplayName("getOaRatio() 全部为 OA 时返回 1")
    void getOaRatioShouldReturnOneWhenAllOa() {
      // Given
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, 100, CITED_BY_COUNT, 100);

      // When
      BigDecimal ratio = metrics.getOaRatio();

      // Then
      assertThat(ratio).isEqualByComparingTo("1.0000");
    }
  }

  @Nested
  @DisplayName("equals() 和 hashCode() 测试")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同年份应该相等（业务相等性）")
    void shouldBeEqualForSameYear() {
      // Given - 不同的计数，相同年份
      VenueMetrics m1 = VenueMetrics.create(2024, 100, 500);
      VenueMetrics m2 = VenueMetrics.create(2024, 200, 1000);

      // Then - 年份相同则相等
      assertThat(m1).isEqualTo(m2);
      assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }

    @Test
    @DisplayName("不同年份应该不相等")
    void shouldNotBeEqualForDifferentYear() {
      // Given
      VenueMetrics m1 = VenueMetrics.create(2023, 100, 500);
      VenueMetrics m2 = VenueMetrics.create(2024, 100, 500);

      // Then
      assertThat(m1).isNotEqualTo(m2);
    }

    @Test
    @DisplayName("与 null 比较应该返回 false")
    void shouldNotBeEqualToNull() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);
      assertThat(metrics).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与不同类型对象比较应该返回 false")
    void shouldNotBeEqualToDifferentType() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);
      assertThat(metrics).isNotEqualTo("not a metrics");
    }

    @Test
    @DisplayName("自反性：对象应该等于自身")
    void shouldBeEqualToItself() {
      VenueMetrics metrics = VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);
      assertThat(metrics).isEqualTo(metrics);
    }
  }

  @Nested
  @DisplayName("toString() 测试")
  class ToStringTests {

    @Test
    @DisplayName("toString() 应该包含关键信息")
    void toStringShouldContainKeyInfo() {
      // Given
      VenueMetrics metrics =
          VenueMetrics.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, OA_WORKS_COUNT);

      // When
      String result = metrics.toString();

      // Then
      assertThat(result).contains(String.valueOf(VALID_YEAR));
      assertThat(result).contains(String.valueOf(WORKS_COUNT));
      assertThat(result).contains(String.valueOf(CITED_BY_COUNT));
      assertThat(result).contains(String.valueOf(OA_WORKS_COUNT));
    }
  }
}
