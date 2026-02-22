package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenuePublicationStats 值对象单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 覆盖工厂方法、验证规则、计算方法和 equals/hashCode
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenuePublicationStats 单元测试")
@Timeout(2)
class VenuePublicationStatsTest {

  // ========== 测试数据 ==========

  private static final int VALID_YEAR = 2024;
  private static final int WORKS_COUNT = 1500;
  private static final int CITED_BY_COUNT = 25000;
  private static final int OA_WORKS_COUNT = 800;

  @Nested
  @DisplayName("create() 工厂方法测试")
  class CreateTests {

    @Test
    @DisplayName("应该正确创建年度统计（含 OA 作品数）")
    void shouldCreateStatsWithOaWorksCount() {
      // When
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, OA_WORKS_COUNT);

      // Then
      assertThat(stats.year()).isEqualTo(VALID_YEAR);
      assertThat(stats.worksCount()).isEqualTo(WORKS_COUNT);
      assertThat(stats.citedByCount()).isEqualTo(CITED_BY_COUNT);
      assertThat(stats.oaWorksCount()).isEqualTo(OA_WORKS_COUNT);
    }

    @Test
    @DisplayName("应该正确创建年度统计（不含 OA 作品数）")
    void shouldCreateStatsWithoutOaWorksCount() {
      // When
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);

      // Then
      assertThat(stats.oaWorksCount()).isNull();
      assertThat(stats.hasOaWorksCount()).isFalse();
    }

    @Test
    @DisplayName("应该正确创建空年度统计")
    void shouldCreateEmptyStats() {
      // When
      VenuePublicationStats stats = VenuePublicationStats.empty(VALID_YEAR);

      // Then
      assertThat(stats.year()).isEqualTo(VALID_YEAR);
      assertThat(stats.worksCount()).isZero();
      assertThat(stats.citedByCount()).isZero();
      assertThat(stats.oaWorksCount()).isNull();
    }
  }

  @Nested
  @DisplayName("年份验证测试")
  class YearValidationTests {

    @Test
    @DisplayName("年份在有效范围内应该通过")
    void shouldAcceptValidYearRange() {
      // 边界值测试
      assertThat(VenuePublicationStats.create(1665, 100, 500).year()).isEqualTo(1665);
      assertThat(VenuePublicationStats.create(2100, 100, 500).year()).isEqualTo(2100);
      assertThat(VenuePublicationStats.create(2024, 100, 500).year()).isEqualTo(2024);
    }

    @Test
    @DisplayName("1900 年前的历史期刊数据应该通过")
    void shouldAcceptHistoricalYears() {
      // OpenAlex 包含 1800 年代甚至更早的学术期刊发文数据
      assertThat(VenuePublicationStats.create(1800, 50, 200).year()).isEqualTo(1800);
      assertThat(VenuePublicationStats.create(1876, 30, 100).year()).isEqualTo(1876);
      assertThat(VenuePublicationStats.create(1899, 100, 500).year()).isEqualTo(1899);
    }

    @Test
    @DisplayName("年份小于 1665 应该抛出异常")
    void shouldRejectYearBefore1665() {
      assertThatThrownBy(() -> VenuePublicationStats.create(1664, 100, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("年份必须在 1665-2100 之间");
    }

    @Test
    @DisplayName("年份大于 2100 应该抛出异常")
    void shouldRejectYearAfter2100() {
      assertThatThrownBy(() -> VenuePublicationStats.create(2101, 100, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("年份必须在 1665-2100 之间");
    }
  }

  @Nested
  @DisplayName("作品数验证测试")
  class WorksCountValidationTests {

    @Test
    @DisplayName("作品数可以为 0")
    void shouldAcceptZeroWorksCount() {
      VenuePublicationStats stats = VenuePublicationStats.create(VALID_YEAR, 0, 0);
      assertThat(stats.worksCount()).isZero();
    }

    @Test
    @DisplayName("作品数为负数应该抛出异常")
    void shouldRejectNegativeWorksCount() {
      assertThatThrownBy(() -> VenuePublicationStats.create(VALID_YEAR, -1, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("发表作品数不能为负数");
    }

    @Test
    @DisplayName("被引次数为负数应该抛出异常")
    void shouldRejectNegativeCitedByCount() {
      assertThatThrownBy(() -> VenuePublicationStats.create(VALID_YEAR, 100, -1))
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
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, null);
      assertThat(stats.oaWorksCount()).isNull();
    }

    @Test
    @DisplayName("OA 作品数可以为 0")
    void shouldAcceptZeroOaWorksCount() {
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, 0);
      assertThat(stats.oaWorksCount()).isZero();
    }

    @Test
    @DisplayName("OA 作品数为负数应该抛出异常")
    void shouldRejectNegativeOaWorksCount() {
      assertThatThrownBy(
              () -> VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OA 作品数不能为负数");
    }

    @Test
    @DisplayName("OA 作品数超过总作品数应该抛出异常")
    void shouldRejectOaWorksCountExceedingTotal() {
      assertThatThrownBy(() -> VenuePublicationStats.create(VALID_YEAR, 100, CITED_BY_COUNT, 150))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OA 作品数不能超过总作品数");
    }

    @Test
    @DisplayName("OA 作品数等于总作品数应该通过")
    void shouldAcceptOaWorksCountEqualToTotal() {
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, 100, CITED_BY_COUNT, 100);
      assertThat(stats.oaWorksCount()).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("with-style 方法测试")
  class WithStyleMethodTests {

    @Test
    @DisplayName("withCounts() 应该返回新实例并更新计数")
    void withCountsShouldReturnNewInstanceWithUpdatedValues() {
      // Given
      VenuePublicationStats stats = VenuePublicationStats.create(VALID_YEAR, 100, 500);

      // When
      VenuePublicationStats updated = stats.withCounts(200, 1000);

      // Then - 返回新实例
      assertThat(updated).isNotSameAs(stats);
      assertThat(updated.worksCount()).isEqualTo(200);
      assertThat(updated.citedByCount()).isEqualTo(1000);
      assertThat(updated.year()).isEqualTo(VALID_YEAR); // 年份不变

      // 原实例不变
      assertThat(stats.worksCount()).isEqualTo(100);
      assertThat(stats.citedByCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("withCounts() 验证负数")
    void withCountsShouldValidateNegativeValues() {
      VenuePublicationStats stats = VenuePublicationStats.create(VALID_YEAR, 100, 500);

      assertThatThrownBy(() -> stats.withCounts(-1, 500))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("发表作品数不能为负数");

      assertThatThrownBy(() -> stats.withCounts(100, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("被引用次数不能为负数");
    }

    @Test
    @DisplayName("withOaWorksCount() 应该返回新实例并设置 OA 作品数")
    void withOaWorksCountShouldReturnNewInstanceWithValue() {
      // Given
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);

      // When
      VenuePublicationStats updated = stats.withOaWorksCount(500);

      // Then - 返回新实例
      assertThat(updated).isNotSameAs(stats);
      assertThat(updated.oaWorksCount()).isEqualTo(500);
      assertThat(updated.hasOaWorksCount()).isTrue();

      // 原实例不变
      assertThat(stats.oaWorksCount()).isNull();
    }

    @Test
    @DisplayName("withOaWorksCount() 可以设置为 null")
    void withOaWorksCountCanSetNull() {
      // Given
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, 500);

      // When
      VenuePublicationStats updated = stats.withOaWorksCount(null);

      // Then
      assertThat(updated.oaWorksCount()).isNull();
      assertThat(updated.hasOaWorksCount()).isFalse();
    }

    @Test
    @DisplayName("withOaWorksCount() 验证约束")
    void withOaWorksCountShouldValidate() {
      VenuePublicationStats stats = VenuePublicationStats.create(VALID_YEAR, 100, CITED_BY_COUNT);

      assertThatThrownBy(() -> stats.withOaWorksCount(-1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OA 作品数不能为负数");

      assertThatThrownBy(() -> stats.withOaWorksCount(150))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OA 作品数不能超过总作品数");
    }
  }

  @Nested
  @DisplayName("计算方法测试")
  class CalculationMethodTests {

    @Test
    @DisplayName("calculateAverageCitations() 应该正确计算平均被引次数")
    void calculateAverageCitationsShouldCalculateCorrectly() {
      // Given - 1500 篇作品，25000 次引用
      VenuePublicationStats stats = VenuePublicationStats.create(VALID_YEAR, 1500, 25000);

      // When
      BigDecimal average = stats.calculateAverageCitations();

      // Then - 25000 / 1500 = 16.67
      assertThat(average).isEqualByComparingTo("16.67");
    }

    @Test
    @DisplayName("calculateAverageCitations() 作品数为 0 时返回 0")
    void calculateAverageCitationsShouldReturnZeroWhenNoWorks() {
      // Given
      VenuePublicationStats stats = VenuePublicationStats.create(VALID_YEAR, 0, 0);

      // When
      BigDecimal average = stats.calculateAverageCitations();

      // Then
      assertThat(average).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getOaRatio() 应该正确计算 OA 比例")
    void getOaRatioShouldCalculateCorrectly() {
      // Given - 1500 篇作品，800 篇 OA
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, 1500, CITED_BY_COUNT, 800);

      // When
      BigDecimal ratio = stats.getOaRatio();

      // Then - 800 / 1500 = 0.5333
      assertThat(ratio).isEqualByComparingTo("0.5333");
    }

    @Test
    @DisplayName("getOaRatio() 作品数为 0 时返回 null")
    void getOaRatioShouldReturnNullWhenNoWorks() {
      // Given
      VenuePublicationStats stats = VenuePublicationStats.create(VALID_YEAR, 0, 0, 0);

      // When
      BigDecimal ratio = stats.getOaRatio();

      // Then
      assertThat(ratio).isNull();
    }

    @Test
    @DisplayName("getOaRatio() 无 OA 数据时返回 null")
    void getOaRatioShouldReturnNullWhenNoOaData() {
      // Given
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);

      // When
      BigDecimal ratio = stats.getOaRatio();

      // Then
      assertThat(ratio).isNull();
    }

    @Test
    @DisplayName("getOaRatio() 全部为 OA 时返回 1")
    void getOaRatioShouldReturnOneWhenAllOa() {
      // Given
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, 100, CITED_BY_COUNT, 100);

      // When
      BigDecimal ratio = stats.getOaRatio();

      // Then
      assertThat(ratio).isEqualByComparingTo("1.0000");
    }
  }

  @Nested
  @DisplayName("equals() 和 hashCode() 测试")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同所有字段应该相等（Record 默认相等性）")
    void shouldBeEqualForSameFields() {
      // Given
      VenuePublicationStats m1 = VenuePublicationStats.create(2024, 100, 500, 50);
      VenuePublicationStats m2 = VenuePublicationStats.create(2024, 100, 500, 50);

      // Then
      assertThat(m1).isEqualTo(m2);
      assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }

    @Test
    @DisplayName("不同年份应该不相等")
    void shouldNotBeEqualForDifferentYear() {
      // Given
      VenuePublicationStats m1 = VenuePublicationStats.create(2023, 100, 500);
      VenuePublicationStats m2 = VenuePublicationStats.create(2024, 100, 500);

      // Then
      assertThat(m1).isNotEqualTo(m2);
    }

    @Test
    @DisplayName("不同作品数应该不相等")
    void shouldNotBeEqualForDifferentWorksCount() {
      // Given
      VenuePublicationStats m1 = VenuePublicationStats.create(2024, 100, 500);
      VenuePublicationStats m2 = VenuePublicationStats.create(2024, 200, 500);

      // Then
      assertThat(m1).isNotEqualTo(m2);
    }

    @Test
    @DisplayName("与 null 比较应该返回 false")
    void shouldNotBeEqualToNull() {
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);
      assertThat(stats).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与不同类型对象比较应该返回 false")
    void shouldNotBeEqualToDifferentType() {
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);
      assertThat(stats).isNotEqualTo("not a stats");
    }

    @Test
    @DisplayName("自反性：对象应该等于自身")
    void shouldBeEqualToItself() {
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT);
      assertThat(stats).isEqualTo(stats);
    }
  }

  @Nested
  @DisplayName("toString() 测试")
  class ToStringTests {

    @Test
    @DisplayName("toString() 应该包含关键信息")
    void toStringShouldContainKeyInfo() {
      // Given
      VenuePublicationStats stats =
          VenuePublicationStats.create(VALID_YEAR, WORKS_COUNT, CITED_BY_COUNT, OA_WORKS_COUNT);

      // When
      String result = stats.toString();

      // Then
      assertThat(result).contains(String.valueOf(VALID_YEAR));
      assertThat(result).contains(String.valueOf(WORKS_COUNT));
      assertThat(result).contains(String.valueOf(CITED_BY_COUNT));
      assertThat(result).contains(String.valueOf(OA_WORKS_COUNT));
    }
  }
}
