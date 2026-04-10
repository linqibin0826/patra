package com.patra.catalog.infra.batch.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// LetPubVenueItemProcessor 单元测试。
///
/// **测试策略**：
///
/// - Mock `LetPubEnrichmentPort`，验证正常/未找到/ISSN空 场景
/// - 验证拆解结果包含 JCR ratings 和 CAS rating
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
    processor = new LetPubVenueItemProcessor(enrichmentPort, new LetPubDataMapper());
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
  @DisplayName("LetPub 查到数据时应返回分开的 JCR 和 CAS 列表")
  void shouldReturnSeparateJcrAndCasResults() throws Exception {
    VenueEntity entity = createVenueEntity(100L, "0028-0836");
    var xinruiPartition =
        LetPubVenueData.CasPartition.builder()
            .version("2026年3月新锐版")
            .majorCategory("综合性期刊")
            .majorQuartile("1区")
            .topJournal(true)
            .reviewJournal(false)
            .build();
    var shengjiPartition =
        LetPubVenueData.CasPartition.builder()
            .version("2025年3月升级版")
            .majorCategory("综合性期刊")
            .majorQuartile("1区")
            .topJournal(true)
            .reviewJournal(false)
            .build();

    LetPubVenueData letPubData =
        LetPubVenueData.builder()
            .letPubJournalId("10000")
            .letPubName("Nature")
            .jifQuartile("Q1")
            .casPartitions(List.of(xinruiPartition, shengjiPartition))
            .impactFactorTrend(Map.of("2024-2025", 48.5))
            .build();
    when(enrichmentPort.findByIssn("0028-0836")).thenReturn(Optional.of(letPubData));

    LetPubEnrichResult result = processor.process(entity);

    assertThat(result).isNotNull();
    assertThat(result.venueId()).isEqualTo(100L);

    // JCR: 1 条（一年的 IF 趋势）
    assertThat(result.jcrRatings()).hasSize(1);
    assertThat(result.jcrRatings().getFirst().getJifQuartile()).isEqualTo("Q1");

    // CAS: 2 条（新锐版 + 升级版）
    assertThat(result.casRatings()).hasSize(2);
    assertThat(result.casRatings())
        .extracting(e -> e.getEdition())
        .containsExactlyInAnyOrder("新锐版", "升级版");
    assertThat(result.casRatings()).allMatch(e -> "1区".equals(e.getMajorQuartile()));

    verify(enrichmentPort).findByIssn("0028-0836");
  }

  @Test
  @DisplayName("LetPub 未找到数据时应返回 null（跳过）")
  void shouldReturnNullWhenNotFound() throws Exception {
    VenueEntity entity = createVenueEntity(200L, "1234-5678");
    when(enrichmentPort.findByIssn("1234-5678")).thenReturn(Optional.empty());

    LetPubEnrichResult result = processor.process(entity);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("ISSN-L 为空时应返回 null（跳过）")
  void shouldReturnNullWhenIssnLBlank() throws Exception {
    VenueEntity entity = createVenueEntity(300L, null);

    LetPubEnrichResult result = processor.process(entity);

    assertThat(result).isNull();
  }
}
