package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// 引用指标值对象单元测试。
///
/// @author linqibin
/// @since 0.7.0
@DisplayName("CitationMetrics 引用指标值对象")
@Timeout(2)
class CitationMetricsTest {

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("of() 应创建完整的引用指标")
    void shouldCreateWithAllFields() {
      // When
      CitationMetrics metrics =
          CitationMetrics.of(150000, 2500000, 285, 1200, new BigDecimal("3.45"));

      // Then
      assertThat(metrics.worksCount()).isEqualTo(150000);
      assertThat(metrics.citedByCount()).isEqualTo(2500000);
      assertThat(metrics.hIndex()).isEqualTo(285);
      assertThat(metrics.i10Index()).isEqualTo(1200);
      assertThat(metrics.twoYearMeanCitedness()).isEqualByComparingTo("3.45");
    }

    @Test
    @DisplayName("ofBasic() 应创建基础引用指标")
    void shouldCreateBasicMetrics() {
      // When
      CitationMetrics metrics = CitationMetrics.ofBasic(150000, 2500000);

      // Then
      assertThat(metrics.worksCount()).isEqualTo(150000);
      assertThat(metrics.citedByCount()).isEqualTo(2500000);
      assertThat(metrics.hIndex()).isNull();
      assertThat(metrics.i10Index()).isNull();
      assertThat(metrics.twoYearMeanCitedness()).isNull();
    }

    @Test
    @DisplayName("empty() 应创建空引用指标")
    void shouldCreateEmptyMetrics() {
      // When
      CitationMetrics metrics = CitationMetrics.empty();

      // Then
      assertThat(metrics.worksCount()).isZero();
      assertThat(metrics.citedByCount()).isZero();
      assertThat(metrics.hIndex()).isNull();
      assertThat(metrics.i10Index()).isNull();
      assertThat(metrics.twoYearMeanCitedness()).isNull();
    }
  }

  @Nested
  @DisplayName("判断方法")
  class PredicateMethodTests {

    @Test
    @DisplayName("hasHIndex() 应正确判断是否有 H 指数")
    void shouldCheckHasHIndex() {
      CitationMetrics withHIndex = CitationMetrics.of(100, 200, 50, null, null);
      CitationMetrics withoutHIndex = CitationMetrics.ofBasic(100, 200);

      assertThat(withHIndex.hasHIndex()).isTrue();
      assertThat(withoutHIndex.hasHIndex()).isFalse();
    }

    @Test
    @DisplayName("hasI10Index() 应正确判断是否有 i10 指数")
    void shouldCheckHasI10Index() {
      CitationMetrics withI10 = CitationMetrics.of(100, 200, null, 30, null);
      CitationMetrics withoutI10 = CitationMetrics.ofBasic(100, 200);

      assertThat(withI10.hasI10Index()).isTrue();
      assertThat(withoutI10.hasI10Index()).isFalse();
    }

    @Test
    @DisplayName("hasTwoYearMeanCitedness() 应正确判断是否有两年平均被引")
    void shouldCheckHasTwoYearMeanCitedness() {
      CitationMetrics withMean = CitationMetrics.of(100, 200, null, null, new BigDecimal("2.5"));
      CitationMetrics withoutMean = CitationMetrics.ofBasic(100, 200);

      assertThat(withMean.hasTwoYearMeanCitedness()).isTrue();
      assertThat(withoutMean.hasTwoYearMeanCitedness()).isFalse();
    }
  }

  @Nested
  @DisplayName("计算方法")
  class CalculationMethodTests {

    @Test
    @DisplayName("getAverageCitations() 应计算平均被引次数")
    void shouldCalculateAverageCitations() {
      CitationMetrics metrics = CitationMetrics.ofBasic(100, 500);

      BigDecimal average = metrics.getAverageCitations();

      assertThat(average).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("getAverageCitations() 当无作品时应返回 0")
    void shouldReturnZeroWhenNoWorks() {
      CitationMetrics noWorks = CitationMetrics.ofBasic(0, 500);
      CitationMetrics nullWorks = CitationMetrics.of(null, 500, null, null, null);

      assertThat(noWorks.getAverageCitations()).isEqualByComparingTo("0");
      assertThat(nullWorks.getAverageCitations()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getAverageCitations() 当无被引次数时应返回 0")
    void shouldReturnZeroWhenNoCitations() {
      CitationMetrics noCitations = CitationMetrics.of(100, null, null, null, null);

      assertThat(noCitations.getAverageCitations()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getAverageCitations() 应正确处理小数")
    void shouldHandleDecimalAverages() {
      CitationMetrics metrics = CitationMetrics.ofBasic(3, 10);

      BigDecimal average = metrics.getAverageCitations();

      assertThat(average).isEqualByComparingTo("3.33");
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("equals 和 hashCode 应基于所有字段")
    void shouldImplementEqualsAndHashCode() {
      CitationMetrics metrics1 = CitationMetrics.of(100, 500, 50, 30, new BigDecimal("2.5"));
      CitationMetrics metrics2 = CitationMetrics.of(100, 500, 50, 30, new BigDecimal("2.5"));
      CitationMetrics metrics3 = CitationMetrics.of(100, 500, 50, 30, new BigDecimal("3.0"));

      assertThat(metrics1).isEqualTo(metrics2);
      assertThat(metrics1.hashCode()).isEqualTo(metrics2.hashCode());
      assertThat(metrics1).isNotEqualTo(metrics3);
    }

    @Test
    @DisplayName("toString 应包含关键字段")
    void shouldHaveToString() {
      CitationMetrics metrics = CitationMetrics.of(100, 500, 50, 30, new BigDecimal("2.5"));
      String str = metrics.toString();

      assertThat(str).contains("100");
      assertThat(str).contains("500");
      assertThat(str).contains("50");
    }
  }
}
