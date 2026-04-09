package com.patra.catalog.infra.batch.venue.scopus;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData.YearlyMetric;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// ScopusDataMapper 单元测试。
///
/// 验证 ScopusVenueData → List<ScopusRatingEntity> 的映射逻辑。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ScopusDataMapper 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class ScopusDataMapperTest {

  private static final Long VENUE_ID = 100L;
  private ScopusDataMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ScopusDataMapper();
  }

  @Nested
  @DisplayName("正常映射测试")
  class NormalMappingTest {

    @Test
    @DisplayName("应该为每个历史年份生成一条 ScopusRatingEntity")
    void shouldMapYearlyMetricsToEntities() {
      // Given
      ScopusVenueData data =
          ScopusVenueData.builder()
              .scopusSourceId("21206")
              .title("Nature")
              .citeScore(78.1)
              .citeScoreTracker(77.2)
              .sjr(18.288)
              .snip(10.161)
              .documentCount(4992)
              .citationCount(390062)
              .percentCited(94.0)
              .subjectArea("Multidisciplinary")
              .quartile("Q1")
              .percentile(99.0)
              .yearlyMetrics(
                  List.of(
                      YearlyMetric.builder()
                          .year(2024)
                          .citeScore(78.1)
                          .documentCount(4992)
                          .citationCount(390062)
                          .percentCited(94.0)
                          .build(),
                      YearlyMetric.builder()
                          .year(2023)
                          .citeScore(90.0)
                          .documentCount(4895)
                          .citationCount(440674)
                          .percentCited(94.0)
                          .build()))
              .build();

      // When
      List<ScopusRatingEntity> entities = mapper.mapToScopusRatings(data, VENUE_ID);

      // Then
      assertThat(entities).hasSize(2);

      // 2024 年（最新年）应填充全部字段
      ScopusRatingEntity latest = entities.get(0);
      assertThat(latest.getVenueId()).isEqualTo(VENUE_ID);
      assertThat(latest.getYear()).isEqualTo((short) 2024);
      assertThat(latest.getCiteScore()).isEqualByComparingTo(new BigDecimal("78.1"));
      assertThat(latest.getCiteScoreTracker()).isEqualByComparingTo(new BigDecimal("77.2"));
      assertThat(latest.getSjr()).isEqualByComparingTo(new BigDecimal("18.288"));
      assertThat(latest.getSnip()).isEqualByComparingTo(new BigDecimal("10.161"));
      assertThat(latest.getDocumentCount()).isEqualTo(4992);
      assertThat(latest.getCitationCount()).isEqualTo(390062);
      assertThat(latest.getPercentCited()).isEqualByComparingTo(new BigDecimal("94.0"));
      assertThat(latest.getSubjectArea()).isEqualTo("Multidisciplinary");
      assertThat(latest.getQuartile()).isEqualTo("Q1");
      assertThat(latest.getPercentile()).isEqualByComparingTo(new BigDecimal("99.0"));
      assertThat(latest.getScopusSourceId()).isEqualTo("21206");

      // 2023 年（历史年）：citeScore + 发文引用，无 SJR/SNIP/Tracker
      ScopusRatingEntity history = entities.get(1);
      assertThat(history.getYear()).isEqualTo((short) 2023);
      assertThat(history.getCiteScore()).isEqualByComparingTo(new BigDecimal("90.0"));
      assertThat(history.getSjr()).isNull();
      assertThat(history.getSnip()).isNull();
      assertThat(history.getCiteScoreTracker()).isNull();
      assertThat(history.getDocumentCount()).isEqualTo(4895);
      assertThat(history.getCitationCount()).isEqualTo(440674);
      assertThat(history.getScopusSourceId()).isEqualTo("21206");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryTest {

    @Test
    @DisplayName("yearlyMetrics 为空时应返回空列表")
    void shouldReturnEmptyListWhenNoYearlyMetrics() {
      // Given
      ScopusVenueData data =
          ScopusVenueData.builder()
              .scopusSourceId("12345")
              .citeScore(5.0)
              .yearlyMetrics(List.of())
              .build();

      // When
      List<ScopusRatingEntity> entities = mapper.mapToScopusRatings(data, VENUE_ID);

      // Then
      assertThat(entities).isEmpty();
    }

    @Test
    @DisplayName("null data 应返回空列表")
    void shouldReturnEmptyListForNullData() {
      assertThat(mapper.mapToScopusRatings(null, VENUE_ID)).isEmpty();
    }
  }
}
