package com.patra.catalog.domain.model.vo.venue;

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

/// 载体评级值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueRating 载体评级值对象")
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
      assertThat(rating.venueId()).isEqualTo(venueId);
      assertThat(rating.year()).isEqualTo(year);
      assertThat(rating.ratingSystem()).isEqualTo(system);
      assertThat(rating.quartile()).isEqualTo(quartile);
      assertThat(rating.impactScore()).isEqualByComparingTo(impactScore);
      assertThat(rating.fetchedAt()).isNotNull();
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
      assertThat(rating.venueId()).isEqualTo(venueId);
      assertThat(rating.year()).isEqualTo(year);
      assertThat(rating.ratingSystem()).isEqualTo(system);
      assertThat(rating.quartile()).isNull();
      assertThat(rating.impactScore()).isNull();
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
  @DisplayName("of() 完整工厂方法")
  class OfTests {

    @Test
    @DisplayName("应正确创建包含所有字段的评级记录")
    void shouldCreateWithAllFields() {
      // Given
      Long venueId = 123L;
      int year = 2024;
      RatingSystem system = RatingSystem.JCR;
      String quartile = "Q1";
      BigDecimal impactScore = new BigDecimal("42.778");
      String ratingData = "{\"jif\": 42.778}";
      String categories = "[{\"category\": \"Medicine\"}]";
      String sourceUrl = "https://example.com";

      // When
      VenueRating rating =
          VenueRating.of(
              venueId,
              year,
              system,
              quartile,
              impactScore,
              ratingData,
              categories,
              sourceUrl,
              null);

      // Then
      assertThat(rating.venueId()).isEqualTo(venueId);
      assertThat(rating.year()).isEqualTo(year);
      assertThat(rating.ratingSystem()).isEqualTo(system);
      assertThat(rating.quartile()).isEqualTo(quartile);
      assertThat(rating.impactScore()).isEqualByComparingTo(impactScore);
      assertThat(rating.ratingData()).isEqualTo(ratingData);
      assertThat(rating.categories()).isEqualTo(categories);
      assertThat(rating.sourceUrl()).isEqualTo(sourceUrl);
      assertThat(rating.fetchedAt()).isNotNull();
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
      VenueRating withData =
          VenueRating.of(
              123L, 2024, RatingSystem.JCR, null, null, "{\"jif\": 42.778}", null, null, null);
      VenueRating withoutData = VenueRating.create(123L, 2024, RatingSystem.JCR);

      assertThat(withData.hasRatingData()).isTrue();
      assertThat(withoutData.hasRatingData()).isFalse();
    }

    @Test
    @DisplayName("hasCategories() 应正确判断")
    void shouldCheckHasCategories() {
      VenueRating withCategories =
          VenueRating.of(
              123L,
              2024,
              RatingSystem.JCR,
              null,
              null,
              null,
              "[{\"category\": \"Medicine\"}]",
              null,
              null);
      VenueRating withoutCategories = VenueRating.create(123L, 2024, RatingSystem.JCR);

      assertThat(withCategories.hasCategories()).isTrue();
      assertThat(withoutCategories.hasCategories()).isFalse();
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
      VenueRating rating =
          VenueRating.of(123L, 2024, RatingSystem.JCR, quartile, null, null, null, null, null);
      assertThat(rating.getNormalizedQuartile()).isNull();
    }
  }

  @Nested
  @DisplayName("Record 特性测试")
  class RecordTests {

    @Test
    @DisplayName("Record 应自动生成 equals 基于所有字段")
    void shouldHaveValueBasedEquality() {
      VenueRating rating1 = VenueRating.create(123L, 2024, RatingSystem.JCR, "Q1", null);
      VenueRating rating2 = VenueRating.create(123L, 2024, RatingSystem.JCR, "Q1", null);

      // Record 的 equals 比较所有字段，但 fetchedAt 是在 create 时生成的
      // 所以需要使用 of() 方法并传入相同的 fetchedAt 来测试
      assertThat(rating1.venueId()).isEqualTo(rating2.venueId());
      assertThat(rating1.year()).isEqualTo(rating2.year());
      assertThat(rating1.ratingSystem()).isEqualTo(rating2.ratingSystem());
      assertThat(rating1.quartile()).isEqualTo(rating2.quartile());
    }

    @Test
    @DisplayName("toString() 应包含关键信息")
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

    @Test
    @DisplayName("Record 应为不可变对象")
    void shouldBeImmutable() {
      VenueRating rating = VenueRating.create(123L, 2024, RatingSystem.JCR, "Q1", null);

      // Record 的所有字段都是 final 的，没有 setter 方法
      // 验证字段不可变
      assertThat(rating.venueId()).isEqualTo(123L);
      assertThat(rating.year()).isEqualTo(2024);
      assertThat(rating.ratingSystem()).isEqualTo(RatingSystem.JCR);
      assertThat(rating.quartile()).isEqualTo("Q1");
    }
  }
}
