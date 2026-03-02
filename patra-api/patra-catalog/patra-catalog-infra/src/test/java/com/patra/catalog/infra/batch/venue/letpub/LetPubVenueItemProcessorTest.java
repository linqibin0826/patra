package com.patra.catalog.infra.batch.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.vo.venue.LetPubVenueData;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// LetPubVenueItemProcessor 单元测试。
///
/// **测试策略**：
///
/// - Mock `LetPubEnrichmentPort`，验证正常/未找到/异常场景
/// - Processor 返回 null 表示跳过（Spring Batch 约定）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubVenueItemProcessor 单元测试")
@Timeout(2)
class LetPubVenueItemProcessorTest {

  private LetPubEnrichmentPort enrichmentPort;
  private LetPubVenueItemProcessor processor;

  @BeforeEach
  void setUp() {
    enrichmentPort = mock(LetPubEnrichmentPort.class);
    processor = new LetPubVenueItemProcessor(enrichmentPort);
  }

  /// 创建测试用 VenueEntity。
  private VenueEntity createVenueEntity(Long id, String issnL) {
    VenueEntity entity = new VenueEntity();
    entity.setId(id);
    entity.setIssnL(issnL);
    entity.setTitle("Test Journal");
    entity.setVenueType("JOURNAL");
    entity.setProvenanceCode("PUBMED");
    return entity;
  }

  @Test
  @DisplayName("LetPub 查到数据时应返回 LetPubEnrichResult")
  void shouldReturnResultWhenDataFound() throws Exception {
    // Given
    VenueEntity entity = createVenueEntity(100L, "0028-0836");
    LetPubVenueData letPubData =
        LetPubVenueData.builder()
            .letPubJournalId("10000")
            .letPubName("Nature")
            .jifQuartile("Q1")
            .indexedIn(List.of("SCI"))
            .build();
    when(enrichmentPort.findByIssn("0028-0836")).thenReturn(Optional.of(letPubData));

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.venueId()).isEqualTo(100L);
    assertThat(result.data()).isEqualTo(letPubData);
    verify(enrichmentPort).findByIssn("0028-0836");
  }

  @Test
  @DisplayName("LetPub 未找到数据时应返回 null（跳过）")
  void shouldReturnNullWhenNotFound() throws Exception {
    // Given
    VenueEntity entity = createVenueEntity(200L, "1234-5678");
    when(enrichmentPort.findByIssn("1234-5678")).thenReturn(Optional.empty());

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("ISSN-L 为空时应返回 null（跳过）")
  void shouldReturnNullWhenIssnLBlank() throws Exception {
    // Given
    VenueEntity entity = createVenueEntity(300L, null);

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNull();
  }
}
