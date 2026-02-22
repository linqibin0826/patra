package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo.ApcPrice;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueOpenAlexEnrichment 值对象单元测试。
///
/// **测试策略**：
///
/// - 工厂方法：验证 `of()` 正确构造
/// - 防御性拷贝：验证 yearlyStats 列表不可变
/// - 判断方法：`hasCitationMetrics()`、`hasYearlyStats()` 边界条件
/// - Record 等值语义：同值相等、异值不等
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueOpenAlexEnrichment 单元测试")
@Timeout(2)
class VenueOpenAlexEnrichmentTest {

  private static final String OPENALEX_ID = "S137773608";
  private static final CitationMetrics SAMPLE_METRICS =
      CitationMetrics.of(150000, 2500000, 285, 1200, new BigDecimal("3.45"));
  private static final List<VenuePublicationStats> SAMPLE_STATS =
      List.of(
          VenuePublicationStats.create(2024, 1500, 25000, 800),
          VenuePublicationStats.create(2023, 1400, 22000, 700));
  private static final OpenAccessInfo SAMPLE_OA_INFO =
      OpenAccessInfo.of(true, true, null, 3000, List.of(ApcPrice.of(3000, "USD")));

  @Nested
  @DisplayName("of() 工厂方法测试")
  class OfFactoryMethodTests {

    @Test
    @DisplayName("应该创建包含所有字段的富化数据")
    void shouldCreateWithAllFields() {
      // When
      var enrichment =
          VenueOpenAlexEnrichment.of(OPENALEX_ID, SAMPLE_METRICS, SAMPLE_STATS, SAMPLE_OA_INFO);

      // Then
      assertThat(enrichment.openAlexId()).isEqualTo(OPENALEX_ID);
      assertThat(enrichment.citationMetrics()).isEqualTo(SAMPLE_METRICS);
      assertThat(enrichment.yearlyStats()).hasSize(2);
      assertThat(enrichment.openAccessInfo()).isEqualTo(SAMPLE_OA_INFO);
    }

    @Test
    @DisplayName("应该允许 citationMetrics 为 null")
    void shouldAllowNullCitationMetrics() {
      // When
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, null, SAMPLE_STATS, SAMPLE_OA_INFO);

      // Then
      assertThat(enrichment.openAlexId()).isEqualTo(OPENALEX_ID);
      assertThat(enrichment.citationMetrics()).isNull();
      assertThat(enrichment.yearlyStats()).hasSize(2);
    }

    @Test
    @DisplayName("应该允许 yearlyStats 为 null（转为空列表）")
    void shouldConvertNullYearlyStatsToEmptyList() {
      // When
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, SAMPLE_METRICS, null, null);

      // Then
      assertThat(enrichment.yearlyStats()).isEmpty();
    }

    @Test
    @DisplayName("应该允许 yearlyStats 为空列表")
    void shouldAcceptEmptyYearlyStats() {
      // When
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, SAMPLE_METRICS, List.of(), null);

      // Then
      assertThat(enrichment.yearlyStats()).isEmpty();
    }

    @Test
    @DisplayName("应该允许 openAccessInfo 为 null")
    void shouldAllowNullOpenAccessInfo() {
      // When
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, SAMPLE_METRICS, SAMPLE_STATS, null);

      // Then
      assertThat(enrichment.openAccessInfo()).isNull();
    }
  }

  @Nested
  @DisplayName("防御性拷贝测试")
  class DefensiveCopyTests {

    @Test
    @DisplayName("yearlyStats 应该是不可变列表")
    void shouldMakeYearlyStatsImmutable() {
      // Given
      var mutableList = new ArrayList<>(SAMPLE_STATS);
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, SAMPLE_METRICS, mutableList, null);

      // When — 修改原始列表
      mutableList.add(VenuePublicationStats.create(2022, 1300, 20000, 600));

      // Then — enrichment 的列表不应受影响
      assertThat(enrichment.yearlyStats()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("hasCitationMetrics() 测试")
  class HasCitationMetricsTests {

    @Test
    @DisplayName("有引用指标时返回 true")
    void shouldReturnTrueWhenHasCitationMetrics() {
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, SAMPLE_METRICS, List.of(), null);
      assertThat(enrichment.hasCitationMetrics()).isTrue();
    }

    @Test
    @DisplayName("null 时返回 false")
    void shouldReturnFalseWhenNull() {
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, null, List.of(), null);
      assertThat(enrichment.hasCitationMetrics()).isFalse();
    }
  }

  @Nested
  @DisplayName("hasYearlyStats() 测试")
  class HasYearlyStatsTests {

    @Test
    @DisplayName("有年度统计时返回 true")
    void shouldReturnTrueWhenHasYearlyStats() {
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, null, SAMPLE_STATS, null);
      assertThat(enrichment.hasYearlyStats()).isTrue();
    }

    @Test
    @DisplayName("空列表时返回 false")
    void shouldReturnFalseWhenEmpty() {
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, null, List.of(), null);
      assertThat(enrichment.hasYearlyStats()).isFalse();
    }

    @Test
    @DisplayName("null 时返回 false（自动转为空列表）")
    void shouldReturnFalseWhenNull() {
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, null, null, null);
      assertThat(enrichment.hasYearlyStats()).isFalse();
    }
  }

  @Nested
  @DisplayName("hasOpenAccessInfo() 测试")
  class HasOpenAccessInfoTests {

    @Test
    @DisplayName("有 OA 信息时返回 true")
    void shouldReturnTrueWhenHasOpenAccessInfo() {
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, null, List.of(), SAMPLE_OA_INFO);
      assertThat(enrichment.hasOpenAccessInfo()).isTrue();
    }

    @Test
    @DisplayName("null 时返回 false")
    void shouldReturnFalseWhenNull() {
      var enrichment = VenueOpenAlexEnrichment.of(OPENALEX_ID, null, List.of(), null);
      assertThat(enrichment.hasOpenAccessInfo()).isFalse();
    }
  }

  @Nested
  @DisplayName("Record 等值语义测试")
  class EqualityTests {

    @Test
    @DisplayName("同值对象应相等")
    void shouldBeEqualWhenSameValues() {
      var a = VenueOpenAlexEnrichment.of(OPENALEX_ID, SAMPLE_METRICS, SAMPLE_STATS, SAMPLE_OA_INFO);
      var b = VenueOpenAlexEnrichment.of(OPENALEX_ID, SAMPLE_METRICS, SAMPLE_STATS, SAMPLE_OA_INFO);
      assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("不同 openAlexId 时不应相等")
    void shouldNotBeEqualWhenDifferentId() {
      var a = VenueOpenAlexEnrichment.of("S137773608", SAMPLE_METRICS, SAMPLE_STATS, null);
      var b = VenueOpenAlexEnrichment.of("S999999999", SAMPLE_METRICS, SAMPLE_STATS, null);
      assertThat(a).isNotEqualTo(b);
    }
  }
}
