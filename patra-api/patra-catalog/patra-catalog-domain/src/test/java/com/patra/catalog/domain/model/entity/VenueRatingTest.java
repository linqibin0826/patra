package com.patra.catalog.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.RatingSystem;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 载体评级实体单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueRating 载体评级实体")
@Timeout(2)
class VenueRatingTest {

  @Nested
  @DisplayName("create() 工厂方法")
  class CreateTests {

    @Test
    @DisplayName("应正确创建完整评级记录")
    void shouldCreateWithAllFields() {
      // Given
      Long venueId = 123L;
      int year = 2024;
      RatingSystem system = RatingSystem.JCR;
      String quartile = "Q1";
      BigDecimal impactScore = new BigDecimal("42.778");

      // When
      VenueRating rating = VenueRating.create(venueId, year, system, quartile, impactScore);

      // Then
      assertThat(rating.getVenueId()).isEqualTo(venueId);
      assertThat(rating.getYear()).isEqualTo(year);
      assertThat(rating.getRatingSystem()).isEqualTo(system);
      assertThat(rating.getQuartile()).isEqualTo(quartile);
      assertThat(rating.getImpactScore()).isEqualByComparingTo(impactScore);
      assertThat(rating.getId()).isNull();
      assertThat(rating.getFetchedAt()).isNotNull();
    }

    @Test
    @DisplayName("应正确创建仅必填字段的评级记录")
    void shouldCreateWithRequiredFieldsOnly() {
      // Given
      Long venueId = 456L;
      int year = 2023;
      RatingSystem system = RatingSystem.CAS;

      // When
      VenueRating rating = VenueRating.create(venueId, year, system);

      // Then
      assertThat(rating.getVenueId()).isEqualTo(venueId);
      assertThat(rating.getYear()).isEqualTo(year);
      assertThat(rating.getRatingSystem()).isEqualTo(system);
      assertThat(rating.getQuartile()).isNull();
      assertThat(rating.getImpactScore()).isNull();
    }

    @Test
    @DisplayName("venueId 为 null 应抛出异常")
    void shouldThrowWhenVenueIdIsNull() {
      assertThatThrownBy(() -> VenueRating.create(null, 2024, RatingSystem.JCR))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Venue ID 不能为空");
    }

    @Test
    @DisplayName("ratingSystem 为 null 应抛出异常")
    void shouldThrowWhenRatingSystemIsNull() {
      assertThatThrownBy(() -> VenueRating.create(123L, 2024, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("评价体系不能为空");
    }

    @ParameterizedTest
    @DisplayName("年份超出范围应抛出异常")
    @ValueSource(ints = {1999, 2101, 0, -1})
    void shouldThrowWhenYearOutOfRange(int year) {
      assertThatThrownBy(() -> VenueRating.create(123L, year, RatingSystem.JCR))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("年份必须在");
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreTests {

    @Test
    @DisplayName("应正确从持久化状态重建实体")
    void shouldRestoreFromPersistence() {
      // Given
      Long id = 1L;
      Long venueId = 123L;
      int year = 2024;
      RatingSystem system = RatingSystem.SCOPUS;

      // When
      VenueRating rating = VenueRating.restore(id, venueId, year, system);

      // Then
      assertThat(rating.getId()).isEqualTo(id);
      assertThat(rating.getVenueId()).isEqualTo(venueId);
      assertThat(rating.getYear()).isEqualTo(year);
      assertThat(rating.getRatingSystem()).isEqualTo(system);
    }
  }

  @Nested
  @DisplayName("链式设置方法")
  class WithMethodsTests {

    @Test
    @DisplayName("withQuartile() 应正确设置分区")
    void shouldSetQuartile() {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR);
      rating.withQuartile("Q2");
      assertThat(rating.getQuartile()).isEqualTo("Q2");
    }

    @Test
    @DisplayName("withImpactScore() 应正确设置影响力分数")
    void shouldSetImpactScore() {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR);
      BigDecimal score = new BigDecimal("15.234");
      rating.withImpactScore(score);
      assertThat(rating.getImpactScore()).isEqualByComparingTo(score);
    }

    @Test
    @DisplayName("withRatingData() 应正确设置评级详情")
    void shouldSetRatingData() {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR);
      String json = "{\"jif\": 42.778}";
      rating.withRatingData(json);
      assertThat(rating.getRatingData()).isEqualTo(json);
    }

    @Test
    @DisplayName("链式调用应正常工作")
    void shouldSupportChaining() {
      VenueRating rating =
          VenueRating.create(123L, 2024, RatingSystem.JCR)
              .withQuartile("Q1")
              .withImpactScore(new BigDecimal("42.778"))
              .withRatingData("{\"jif\": 42.778}")
              .withCategories("[{\"category\": \"Medicine\"}]");

      assertThat(rating.getQuartile()).isEqualTo("Q1");
      assertThat(rating.getImpactScore()).isEqualByComparingTo(new BigDecimal("42.778"));
      assertThat(rating.getRatingData()).isNotBlank();
      assertThat(rating.getCategories()).isNotBlank();
    }
  }

  @Nested
  @DisplayName("判断方法")
  class HasMethodsTests {

    @Test
    @DisplayName("hasQuartile() 应正确判断")
    void shouldCheckHasQuartile() {
      VenueRating withQuartile = VenueRating.create(123L, 2024, RatingSystem.JCR, "Q1", null);
      VenueRating withoutQuartile = VenueRating.create(123L, 2024, RatingSystem.JCR);

      assertThat(withQuartile.hasQuartile()).isTrue();
      assertThat(withoutQuartile.hasQuartile()).isFalse();
    }

    @Test
    @DisplayName("hasImpactScore() 应正确判断")
    void shouldCheckHasImpactScore() {
      VenueRating withScore =
          VenueRating.create(123L, 2024, RatingSystem.JCR, null, new BigDecimal("42.778"));
      VenueRating withoutScore = VenueRating.create(123L, 2024, RatingSystem.JCR);

      assertThat(withScore.hasImpactScore()).isTrue();
      assertThat(withoutScore.hasImpactScore()).isFalse();
    }

    @Test
    @DisplayName("hasRatingData() 应正确判断")
    void shouldCheckHasRatingData() {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR);
      assertThat(rating.hasRatingData()).isFalse();

      rating.withRatingData("{\"jif\": 42.778}");
      assertThat(rating.hasRatingData()).isTrue();
    }
  }

  @Nested
  @DisplayName("评价体系判断")
  class RatingSystemTypeTests {

    @Test
    @DisplayName("isJcrRating() 应正确判断")
    void shouldIdentifyJcrRating() {
      VenueRating jcr = VenueRating.create(123L, 2024, RatingSystem.JCR);
      VenueRating cas = VenueRating.create(123L, 2024, RatingSystem.CAS);

      assertThat(jcr.isJcrRating()).isTrue();
      assertThat(cas.isJcrRating()).isFalse();
    }

    @Test
    @DisplayName("isCasRating() 应正确判断")
    void shouldIdentifyCasRating() {
      VenueRating cas = VenueRating.create(123L, 2024, RatingSystem.CAS);
      VenueRating jcr = VenueRating.create(123L, 2024, RatingSystem.JCR);

      assertThat(cas.isCasRating()).isTrue();
      assertThat(jcr.isCasRating()).isFalse();
    }

    @Test
    @DisplayName("isScopusRating() 应正确判断")
    void shouldIdentifyScopusRating() {
      VenueRating scopus = VenueRating.create(123L, 2024, RatingSystem.SCOPUS);
      VenueRating jcr = VenueRating.create(123L, 2024, RatingSystem.JCR);

      assertThat(scopus.isScopusRating()).isTrue();
      assertThat(jcr.isScopusRating()).isFalse();
    }
  }

  @Nested
  @DisplayName("顶级分区判断")
  class IsTopQuartileTests {

    @ParameterizedTest(name = "分区 \"{0}\" 应为顶级分区")
    @DisplayName("应识别顶级分区")
    @ValueSource(strings = {"Q1", "q1", "1区", "1"})
    void shouldIdentifyTopQuartile(String quartile) {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR, quartile, null);
      assertThat(rating.isTopQuartile()).isTrue();
    }

    @ParameterizedTest(name = "分区 \"{0}\" 不应为顶级分区")
    @DisplayName("应识别非顶级分区")
    @ValueSource(strings = {"Q2", "Q3", "Q4", "2区", "3区", "4区", "2", "3", "4"})
    void shouldIdentifyNonTopQuartile(String quartile) {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR, quartile, null);
      assertThat(rating.isTopQuartile()).isFalse();
    }

    @Test
    @DisplayName("空分区应返回 false")
    void shouldReturnFalseForNullQuartile() {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR);
      assertThat(rating.isTopQuartile()).isFalse();
    }
  }

  @Nested
  @DisplayName("分区标准化")
  class GetNormalizedQuartileTests {

    @ParameterizedTest(name = "分区 \"{0}\" 应标准化为 \"{1}\"")
    @DisplayName("应正确标准化分区格式")
    @CsvSource({
      "Q1, Q1", "q2, Q2", "1区, Q1", "2区, Q2", "3区, Q3", "4区, Q4", "1, Q1", "2, Q2", "3, Q3", "4, Q4"
    })
    void shouldNormalizeQuartile(String input, String expected) {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR, input, null);
      assertThat(rating.getNormalizedQuartile()).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("空分区应返回 null")
    @NullAndEmptySource
    void shouldReturnNullForBlankQuartile(String quartile) {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR);
      if (quartile != null) {
        rating.withQuartile(quartile);
      }
      assertThat(rating.getNormalizedQuartile()).isNull();
    }
  }

  @Nested
  @DisplayName("equals 和 hashCode")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同 venueId + year + ratingSystem 应相等")
    void shouldBeEqualWithSameBusinessKey() {
      VenueRating rating1 = VenueRating.create(123L, 2024, RatingSystem.JCR, "Q1", null);
      VenueRating rating2 = VenueRating.create(123L, 2024, RatingSystem.JCR, "Q2", null);

      assertThat(rating1).isEqualTo(rating2);
      assertThat(rating1.hashCode()).isEqualTo(rating2.hashCode());
    }

    @Test
    @DisplayName("不同 venueId 应不相等")
    void shouldNotBeEqualWithDifferentVenueId() {
      VenueRating rating1 = VenueRating.create(123L, 2024, RatingSystem.JCR);
      VenueRating rating2 = VenueRating.create(456L, 2024, RatingSystem.JCR);

      assertThat(rating1).isNotEqualTo(rating2);
    }

    @Test
    @DisplayName("不同 year 应不相等")
    void shouldNotBeEqualWithDifferentYear() {
      VenueRating rating1 = VenueRating.create(123L, 2024, RatingSystem.JCR);
      VenueRating rating2 = VenueRating.create(123L, 2023, RatingSystem.JCR);

      assertThat(rating1).isNotEqualTo(rating2);
    }

    @Test
    @DisplayName("不同 ratingSystem 应不相等")
    void shouldNotBeEqualWithDifferentSystem() {
      VenueRating rating1 = VenueRating.create(123L, 2024, RatingSystem.JCR);
      VenueRating rating2 = VenueRating.create(123L, 2024, RatingSystem.CAS);

      assertThat(rating1).isNotEqualTo(rating2);
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {

    @Test
    @DisplayName("应包含关键信息")
    void shouldContainKeyInfo() {
      VenueRating rating =
          VenueRating.create(123L, 2024, RatingSystem.JCR, "Q1", new BigDecimal("42.778"));

      String str = rating.toString();
      assertThat(str).contains("123");
      assertThat(str).contains("2024");
      assertThat(str).contains("JCR");
      assertThat(str).contains("Q1");
      assertThat(str).contains("42.778");
    }
  }
}
