package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// VenueRatingAggregate 聚合根单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("VenueRatingAggregate 载体评级聚合根")
@Timeout(2)
class VenueRatingAggregateTest {

  @Nested
  @DisplayName("VenueRatingId 值对象")
  class VenueRatingIdTests {

    @Test
    @DisplayName("应正确创建 VenueRatingId")
    void shouldCreateVenueRatingId() {
      // When
      VenueRatingId id = VenueRatingId.of(12345L);

      // Then
      assertThat(id.value()).isEqualTo(12345L);
      assertThat(id.toString()).isEqualTo("12345");
    }

    @Test
    @DisplayName("null 值应抛出异常")
    void shouldThrowWhenValueIsNull() {
      assertThatThrownBy(() -> VenueRatingId.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("不能为空");
    }

    @ParameterizedTest
    @DisplayName("非正整数应抛出异常")
    @ValueSource(longs = {0, -1, -100})
    void shouldThrowWhenValueIsNotPositive(long value) {
      assertThatThrownBy(() -> VenueRatingId.of(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须为正整数");
    }

    @Test
    @DisplayName("相同值的 VenueRatingId 应相等")
    void shouldBeEqualWhenValuesSame() {
      VenueRatingId id1 = VenueRatingId.of(123L);
      VenueRatingId id2 = VenueRatingId.of(123L);

      assertThat(id1).isEqualTo(id2);
      assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
  }

  @Nested
  @DisplayName("create() 工厂方法")
  class CreateTests {

    @Test
    @DisplayName("应正确创建评级聚合根")
    void shouldCreateAggregate() {
      // Given
      VenueId venueId = VenueId.of(123L);
      int year = 2024;
      RatingSystem system = RatingSystem.JCR;
      String quartile = "Q1";
      BigDecimal impactScore = new BigDecimal("42.778");

      // When
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(venueId, year, system, quartile, impactScore);

      // Then
      assertThat(aggregate.getId()).isNull(); // 新创建的聚合根 ID 为 null
      assertThat(aggregate.isTransient()).isTrue();
      assertThat(aggregate.getVenueId()).isEqualTo(venueId);
      assertThat(aggregate.getYear()).isEqualTo(year);
      assertThat(aggregate.getRatingSystem()).isEqualTo(system);
      assertThat(aggregate.getQuartile()).isEqualTo(quartile);
      assertThat(aggregate.getImpactScore()).isEqualByComparingTo(impactScore);
      assertThat(aggregate.getFetchedAt()).isNotNull();
    }

    @Test
    @DisplayName("venueId 为 null 应抛出异常")
    void shouldThrowWhenVenueIdIsNull() {
      assertThatThrownBy(
              () -> VenueRatingAggregate.create(null, 2024, RatingSystem.JCR, "Q1", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Venue ID 不能为空");
    }

    @Test
    @DisplayName("ratingSystem 为 null 应抛出异常")
    void shouldThrowWhenRatingSystemIsNull() {
      assertThatThrownBy(
              () -> VenueRatingAggregate.create(VenueId.of(123L), 2024, null, "Q1", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("评价体系不能为空");
    }

    @ParameterizedTest
    @DisplayName("年份超出范围应抛出异常")
    @ValueSource(ints = {1999, 2101, 0, -1})
    void shouldThrowWhenYearOutOfRange(int year) {
      assertThatThrownBy(
              () ->
                  VenueRatingAggregate.create(VenueId.of(123L), year, RatingSystem.JCR, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("年份必须在 2000-2100 范围内");
    }

    @ParameterizedTest
    @DisplayName("边界年份应正常创建")
    @ValueSource(ints = {2000, 2100})
    void shouldCreateWithBoundaryYears(int year) {
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), year, RatingSystem.JCR, null, null);

      assertThat(aggregate.getYear()).isEqualTo(year);
    }
  }

  @Nested
  @DisplayName("restore() 恢复方法")
  class RestoreTests {

    @Test
    @DisplayName("应正确从持久化状态恢复聚合根")
    void shouldRestoreFromPersistence() {
      // Given
      VenueRatingId id = VenueRatingId.of(999L);
      VenueId venueId = VenueId.of(123L);
      int year = 2024;
      RatingSystem system = RatingSystem.JCR;
      Long version = 5L;

      // When
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.restore(id, venueId, year, system, version);

      // Then
      assertThat(aggregate.getId()).isEqualTo(id);
      assertThat(aggregate.isTransient()).isFalse();
      assertThat(aggregate.getVenueId()).isEqualTo(venueId);
      assertThat(aggregate.getYear()).isEqualTo(year);
      assertThat(aggregate.getRatingSystem()).isEqualTo(system);
      assertThat(aggregate.getVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("restoreState() 应正确恢复可变状态")
    void shouldRestoreState() {
      // Given
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.restore(
              VenueRatingId.of(999L), VenueId.of(123L), 2024, RatingSystem.JCR, 1L);
      String quartile = "Q1";
      BigDecimal impactScore = new BigDecimal("42.778");
      String ratingData = "{\"jif\": 42.778}";
      String categories = "[{\"category\": \"Medicine\"}]";
      String sourceUrl = "https://example.com";
      Instant fetchedAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      aggregate.restoreState(quartile, impactScore, ratingData, categories, sourceUrl, fetchedAt);

      // Then
      assertThat(aggregate.getQuartile()).isEqualTo(quartile);
      assertThat(aggregate.getImpactScore()).isEqualByComparingTo(impactScore);
      assertThat(aggregate.getRatingData()).isEqualTo(ratingData);
      assertThat(aggregate.getCategories()).isEqualTo(categories);
      assertThat(aggregate.getSourceUrl()).isEqualTo(sourceUrl);
      assertThat(aggregate.getFetchedAt()).isEqualTo(fetchedAt);
      // restoreState 不应标记为脏
      assertThat(aggregate.isDirty()).isFalse();
    }
  }

  @Nested
  @DisplayName("业务方法")
  class BusinessMethodTests {

    @Test
    @DisplayName("updateRatingDetails() 应更新评级详情并标记脏")
    void shouldUpdateRatingDetails() {
      // Given
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      String ratingData = "{\"jif\": 42.778}";
      String categories = "[{\"category\": \"Medicine\"}]";

      // When
      aggregate.updateRatingDetails(ratingData, categories);

      // Then
      assertThat(aggregate.getRatingData()).isEqualTo(ratingData);
      assertThat(aggregate.getCategories()).isEqualTo(categories);
      assertThat(aggregate.isDirty()).isTrue();
    }

    @Test
    @DisplayName("updateQuartileAndScore() 应更新分区和分数并标记脏")
    void shouldUpdateQuartileAndScore() {
      // Given
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      String quartile = "Q1";
      BigDecimal impactScore = new BigDecimal("42.778");

      // When
      aggregate.updateQuartileAndScore(quartile, impactScore);

      // Then
      assertThat(aggregate.getQuartile()).isEqualTo(quartile);
      assertThat(aggregate.getImpactScore()).isEqualByComparingTo(impactScore);
      assertThat(aggregate.isDirty()).isTrue();
    }

    @Test
    @DisplayName("recordSource() 应记录数据来源并标记脏")
    void shouldRecordSource() {
      // Given
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      String sourceUrl = "https://jcr.clarivate.com";
      Instant fetchedAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      aggregate.recordSource(sourceUrl, fetchedAt);

      // Then
      assertThat(aggregate.getSourceUrl()).isEqualTo(sourceUrl);
      assertThat(aggregate.getFetchedAt()).isEqualTo(fetchedAt);
      assertThat(aggregate.isDirty()).isTrue();
    }

    @Test
    @DisplayName("recordSource() 在 fetchedAt 为 null 时应使用当前时间")
    void shouldUseCurrentTimeWhenFetchedAtIsNull() {
      // Given
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      Instant before = Instant.now();

      // When
      aggregate.recordSource("https://example.com", null);

      // Then
      assertThat(aggregate.getFetchedAt()).isAfterOrEqualTo(before);
      assertThat(aggregate.getFetchedAt()).isBeforeOrEqualTo(Instant.now());
    }
  }

  @Nested
  @DisplayName("判断方法")
  class QueryMethodTests {

    @Test
    @DisplayName("hasQuartile() 应正确判断")
    void shouldCheckHasQuartile() {
      VenueRatingAggregate withQuartile =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, "Q1", null);
      VenueRatingAggregate withoutQuartile =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);

      assertThat(withQuartile.hasQuartile()).isTrue();
      assertThat(withoutQuartile.hasQuartile()).isFalse();
    }

    @Test
    @DisplayName("hasImpactScore() 应正确判断")
    void shouldCheckHasImpactScore() {
      VenueRatingAggregate withScore =
          VenueRatingAggregate.create(
              VenueId.of(123L), 2024, RatingSystem.JCR, null, new BigDecimal("42.778"));
      VenueRatingAggregate withoutScore =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);

      assertThat(withScore.hasImpactScore()).isTrue();
      assertThat(withoutScore.hasImpactScore()).isFalse();
    }

    @Test
    @DisplayName("hasRatingData() 应正确判断")
    void shouldCheckHasRatingData() {
      // Given
      VenueRatingAggregate withRatingData =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      withRatingData.updateRatingDetails("{\"jif\": 42.778}", null);

      VenueRatingAggregate withoutRatingData =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);

      VenueRatingAggregate withEmptyRatingData =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      withEmptyRatingData.updateRatingDetails("", null);

      VenueRatingAggregate withBlankRatingData =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      withBlankRatingData.updateRatingDetails("   ", null);

      // Then
      assertThat(withRatingData.hasRatingData()).isTrue();
      assertThat(withoutRatingData.hasRatingData()).isFalse();
      assertThat(withEmptyRatingData.hasRatingData()).isFalse();
      assertThat(withBlankRatingData.hasRatingData()).isFalse();
    }

    @Test
    @DisplayName("hasCategories() 应正确判断")
    void shouldCheckHasCategories() {
      // Given
      VenueRatingAggregate withCategories =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      withCategories.updateRatingDetails(null, "[{\"category\": \"Medicine\"}]");

      VenueRatingAggregate withoutCategories =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);

      VenueRatingAggregate withEmptyCategories =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      withEmptyCategories.updateRatingDetails(null, "");

      VenueRatingAggregate withBlankCategories =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      withBlankCategories.updateRatingDetails(null, "   ");

      // Then
      assertThat(withCategories.hasCategories()).isTrue();
      assertThat(withoutCategories.hasCategories()).isFalse();
      assertThat(withEmptyCategories.hasCategories()).isFalse();
      assertThat(withBlankCategories.hasCategories()).isFalse();
    }

    @Test
    @DisplayName("isJcrRating() 应正确判断")
    void shouldIdentifyJcrRating() {
      VenueRatingAggregate jcr =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      VenueRatingAggregate cas =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.CAS, null, null);

      assertThat(jcr.isJcrRating()).isTrue();
      assertThat(cas.isJcrRating()).isFalse();
    }

    @Test
    @DisplayName("isCasRating() 应正确判断")
    void shouldIdentifyCasRating() {
      VenueRatingAggregate cas =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.CAS, null, null);
      VenueRatingAggregate jcr =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);

      assertThat(cas.isCasRating()).isTrue();
      assertThat(jcr.isCasRating()).isFalse();
    }

    @Test
    @DisplayName("isScopusRating() 应正确判断")
    void shouldIdentifyScopusRating() {
      VenueRatingAggregate scopus =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.SCOPUS, null, null);
      VenueRatingAggregate jcr =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);

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
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, quartile, null);
      assertThat(aggregate.isTopQuartile()).isTrue();
    }

    @ParameterizedTest(name = "分区 \"{0}\" 不应为顶级分区")
    @DisplayName("应识别非顶级分区")
    @ValueSource(strings = {"Q2", "Q3", "Q4", "2区", "3区", "4区", "2", "3", "4"})
    void shouldIdentifyNonTopQuartile(String quartile) {
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, quartile, null);
      assertThat(aggregate.isTopQuartile()).isFalse();
    }

    @Test
    @DisplayName("空分区应返回 false")
    void shouldReturnFalseForNullQuartile() {
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      assertThat(aggregate.isTopQuartile()).isFalse();
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
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, input, null);
      assertThat(aggregate.getNormalizedQuartile()).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("空分区应返回 null")
    @NullAndEmptySource
    void shouldReturnNullForBlankQuartile(String quartile) {
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, quartile, null);
      assertThat(aggregate.getNormalizedQuartile()).isNull();
    }
  }

  @Nested
  @DisplayName("聚合根基类功能")
  class AggregateRootTests {

    @Test
    @DisplayName("assignId() 应正确分配 ID")
    void shouldAssignId() {
      // Given
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      VenueRatingId id = VenueRatingId.of(999L);

      // When
      aggregate.assignId(id);

      // Then
      assertThat(aggregate.getId()).isEqualTo(id);
      assertThat(aggregate.isTransient()).isFalse();
    }

    @Test
    @DisplayName("clearDirty() 应清除脏标记")
    void shouldClearDirtyFlag() {
      // Given
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(VenueId.of(123L), 2024, RatingSystem.JCR, null, null);
      aggregate.updateQuartileAndScore("Q1", new BigDecimal("42.778"));
      assertThat(aggregate.isDirty()).isTrue();

      // When
      aggregate.clearDirty();

      // Then
      assertThat(aggregate.isDirty()).isFalse();
    }
  }
}
