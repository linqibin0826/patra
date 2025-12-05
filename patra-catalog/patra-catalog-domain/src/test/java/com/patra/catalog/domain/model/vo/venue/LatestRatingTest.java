package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 最新评级快照值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LatestRating 最新评级快照值对象")
@Timeout(2)
class LatestRatingTest {

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("of() 应创建完整的评级快照")
    void shouldCreateWithAllFields() {
      // Given
      BigDecimal impactScore = new BigDecimal("42.778");

      // When
      LatestRating rating = LatestRating.of(impactScore, "Q1", "JCR", 2023);

      // Then
      assertThat(rating.impactScore()).isEqualByComparingTo(impactScore);
      assertThat(rating.quartile()).isEqualTo("Q1");
      assertThat(rating.ratingSystem()).isEqualTo("JCR");
      assertThat(rating.year()).isEqualTo(2023);
    }

    @Test
    @DisplayName("empty() 应返回空评级快照")
    void shouldCreateEmptyRating() {
      // When
      LatestRating rating = LatestRating.empty();

      // Then
      assertThat(rating.impactScore()).isNull();
      assertThat(rating.quartile()).isNull();
      assertThat(rating.ratingSystem()).isNull();
      assertThat(rating.year()).isNull();
    }

    @Test
    @DisplayName("empty() 应返回单例")
    void shouldReturnSameEmptyInstance() {
      LatestRating empty1 = LatestRating.empty();
      LatestRating empty2 = LatestRating.empty();

      assertThat(empty1).isSameAs(empty2);
    }
  }

  @Nested
  @DisplayName("判断方法")
  class HasMethodTests {

    @Test
    @DisplayName("hasRating() 应正确判断是否有评级数据")
    void shouldCheckHasRating() {
      LatestRating withRating = LatestRating.of(null, null, "JCR", 2023);
      LatestRating withoutSystem = LatestRating.of(null, null, null, 2023);
      LatestRating withoutYear = LatestRating.of(null, null, "JCR", null);
      LatestRating empty = LatestRating.empty();

      assertThat(withRating.hasRating()).isTrue();
      assertThat(withoutSystem.hasRating()).isFalse();
      assertThat(withoutYear.hasRating()).isFalse();
      assertThat(empty.hasRating()).isFalse();
    }

    @Test
    @DisplayName("hasImpactScore() 应正确判断是否有影响力分数")
    void shouldCheckHasImpactScore() {
      LatestRating withScore = LatestRating.of(new BigDecimal("42.778"), null, "JCR", 2023);
      LatestRating withoutScore = LatestRating.of(null, "Q1", "JCR", 2023);

      assertThat(withScore.hasImpactScore()).isTrue();
      assertThat(withoutScore.hasImpactScore()).isFalse();
    }

    @Test
    @DisplayName("hasQuartile() 应正确判断是否有分区信息")
    void shouldCheckHasQuartile() {
      LatestRating withQuartile = LatestRating.of(null, "Q1", "JCR", 2023);
      LatestRating withoutQuartile = LatestRating.of(new BigDecimal("42.778"), null, "JCR", 2023);

      assertThat(withQuartile.hasQuartile()).isTrue();
      assertThat(withoutQuartile.hasQuartile()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("空白分区应返回 false")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldReturnFalseForBlankQuartile(String quartile) {
      LatestRating rating = LatestRating.of(null, quartile, "JCR", 2023);
      assertThat(rating.hasQuartile()).isFalse();
    }
  }

  @Nested
  @DisplayName("评价体系判断")
  class RatingSystemTests {

    @Test
    @DisplayName("isJcr() 应正确判断 JCR 评级")
    void shouldIdentifyJcr() {
      LatestRating jcr = LatestRating.of(null, null, "JCR", 2023);
      LatestRating cas = LatestRating.of(null, null, "CAS", 2023);

      assertThat(jcr.isJcr()).isTrue();
      assertThat(cas.isJcr()).isFalse();
    }

    @Test
    @DisplayName("isCas() 应正确判断中科院分区")
    void shouldIdentifyCas() {
      LatestRating cas = LatestRating.of(null, null, "CAS", 2023);
      LatestRating jcr = LatestRating.of(null, null, "JCR", 2023);

      assertThat(cas.isCas()).isTrue();
      assertThat(jcr.isCas()).isFalse();
    }

    @Test
    @DisplayName("isScopus() 应正确判断 Scopus 评级")
    void shouldIdentifyScopus() {
      LatestRating scopus = LatestRating.of(null, null, "SCOPUS", 2023);
      LatestRating jcr = LatestRating.of(null, null, "JCR", 2023);

      assertThat(scopus.isScopus()).isTrue();
      assertThat(jcr.isScopus()).isFalse();
    }
  }

  @Nested
  @DisplayName("顶级分区判断")
  class IsTopQuartileTests {

    @ParameterizedTest(name = "分区 \"{0}\" 应为顶级分区")
    @DisplayName("应识别顶级分区")
    @ValueSource(strings = {"Q1", "q1", "1区", "1"})
    void shouldIdentifyTopQuartile(String quartile) {
      LatestRating rating = LatestRating.of(null, quartile, "JCR", 2023);
      assertThat(rating.isTopQuartile()).isTrue();
    }

    @ParameterizedTest(name = "分区 \"{0}\" 不应为顶级分区")
    @DisplayName("应识别非顶级分区")
    @ValueSource(strings = {"Q2", "Q3", "Q4", "2区", "3区", "4区"})
    void shouldIdentifyNonTopQuartile(String quartile) {
      LatestRating rating = LatestRating.of(null, quartile, "JCR", 2023);
      assertThat(rating.isTopQuartile()).isFalse();
    }

    @Test
    @DisplayName("空分区应返回 false")
    void shouldReturnFalseForNullQuartile() {
      LatestRating rating = LatestRating.of(null, null, "JCR", 2023);
      assertThat(rating.isTopQuartile()).isFalse();
    }
  }

  @Nested
  @DisplayName("getQuartileLevel()")
  class GetQuartileLevelTests {

    @ParameterizedTest(name = "分区 \"{0}\" 应解析为等级 {1}")
    @DisplayName("应正确解析分区等级")
    @CsvSource({
      "Q1, 1", "Q2, 2", "Q3, 3", "Q4, 4", "q1, 1", "q2, 2", "1区, 1", "2区, 2", "3区, 3", "4区, 4",
      "1, 1", "2, 2", "3, 3", "4, 4"
    })
    void shouldParseQuartileLevel(String quartile, int expectedLevel) {
      LatestRating rating = LatestRating.of(null, quartile, "JCR", 2023);
      assertThat(rating.getQuartileLevel()).isEqualTo(expectedLevel);
    }

    @Test
    @DisplayName("无效分区应返回 null")
    void shouldReturnNullForInvalidQuartile() {
      LatestRating rating = LatestRating.of(null, "Invalid", "JCR", 2023);
      assertThat(rating.getQuartileLevel()).isNull();
    }

    @Test
    @DisplayName("空分区应返回 null")
    void shouldReturnNullForNullQuartile() {
      LatestRating rating = LatestRating.of(null, null, "JCR", 2023);
      assertThat(rating.getQuartileLevel()).isNull();
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("equals 和 hashCode 应基于所有字段")
    void shouldImplementEqualsAndHashCode() {
      LatestRating rating1 = LatestRating.of(new BigDecimal("42.778"), "Q1", "JCR", 2023);
      LatestRating rating2 = LatestRating.of(new BigDecimal("42.778"), "Q1", "JCR", 2023);
      LatestRating rating3 = LatestRating.of(new BigDecimal("42.778"), "Q2", "JCR", 2023);

      assertThat(rating1).isEqualTo(rating2);
      assertThat(rating1.hashCode()).isEqualTo(rating2.hashCode());
      assertThat(rating1).isNotEqualTo(rating3);
    }

    @Test
    @DisplayName("toString 应包含所有字段")
    void shouldHaveToString() {
      LatestRating rating = LatestRating.of(new BigDecimal("42.778"), "Q1", "JCR", 2023);
      String str = rating.toString();

      assertThat(str).contains("42.778");
      assertThat(str).contains("Q1");
      assertThat(str).contains("JCR");
      assertThat(str).contains("2023");
    }
  }
}
