package com.patra.catalog.infra.batch.venue.scopus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData.YearlyMetric;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// ScopusVenueItemProcessor 单元测试。
///
/// 验证 Processor 的核心流程：VenueEntity → 调用 Port → DataMapper → ScopusEnrichResult。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ScopusVenueItemProcessor 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class ScopusVenueItemProcessorTest {

  @Mock private ScopusEnrichmentPort enrichmentPort;

  private ScopusVenueItemProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new ScopusVenueItemProcessor(enrichmentPort, new ScopusDataMapper());
  }

  @Nested
  @DisplayName("正常处理测试")
  class NormalProcessTest {

    @Test
    @DisplayName("应该正确处理找到的期刊数据")
    void shouldProcessFoundVenue() throws Exception {
      // Given
      VenueEntity venue = new VenueEntity();
      venue.setId(100L);
      venue.setIssnL("0028-0836");

      ScopusVenueData data =
          ScopusVenueData.builder()
              .scopusSourceId("21206")
              .title("Nature")
              .citeScore(78.1)
              .sjr(18.288)
              .snip(10.161)
              .yearlyMetrics(
                  List.of(
                      YearlyMetric.builder()
                          .year(2024)
                          .citeScore(78.1)
                          .documentCount(4992)
                          .citationCount(390062)
                          .build()))
              .build();

      when(enrichmentPort.findByIssn("0028-0836")).thenReturn(Optional.of(data));

      // When
      ScopusEnrichResult result = processor.process(venue);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.venueId()).isEqualTo(100L);
      assertThat(result.scopusRatings()).hasSize(1);
      assertThat(result.scopusRatings().getFirst().getCiteScore()).isEqualByComparingTo("78.1");
    }
  }

  @Nested
  @DisplayName("跳过场景测试")
  class SkipTest {

    @Test
    @DisplayName("ISSN-L 为空时应返回 null（跳过）")
    void shouldReturnNullWhenIssnBlank() throws Exception {
      // Given
      VenueEntity venue = new VenueEntity();
      venue.setId(100L);
      venue.setIssnL("");

      // When
      ScopusEnrichResult result = processor.process(venue);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Port 未找到数据时应返回 null（跳过）")
    void shouldReturnNullWhenNotFound() throws Exception {
      // Given
      VenueEntity venue = new VenueEntity();
      venue.setId(100L);
      venue.setIssnL("0000-0000");

      when(enrichmentPort.findByIssn(anyString())).thenReturn(Optional.empty());

      // When
      ScopusEnrichResult result = processor.process(venue);

      // Then
      assertThat(result).isNull();
    }
  }
}
