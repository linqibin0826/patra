package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// LetPub 期刊富化 Processor。
///
/// 从 {@link LetPubEnrichmentPort} 按 ISSN-L 查询 LetPub 数据，
/// 然后通过 {@link LetPubDataMapper} 拆解为 JCR + CAS 评级实体。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemProcessor implements ItemProcessor<VenueEntity, LetPubEnrichResult> {

  private final LetPubEnrichmentPort enrichmentPort;
  private final LetPubDataMapper dataMapper;

  /// 处理单个 VenueEntity，通过 ISSN-L 查询 LetPub 数据并拆解为评级行。
  @Override
  public LetPubEnrichResult process(VenueEntity item) throws Exception {
    String issnL = item.getIssnL();
    if (issnL == null || issnL.isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 LetPub 富化", item.getId());
      return null;
    }

    Optional<LetPubVenueData> result = enrichmentPort.findByIssn(issnL);
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 LetPub 找到数据", item.getId(), issnL);
      return null;
    }

    LetPubVenueData data = result.get();
    Long venueId = item.getId();
    String sourceUrl = buildSourceUrl(data.letPubJournalId());

    List<JcrRatingEntity> jcrRatings = dataMapper.mapToJcrRatings(data, venueId, sourceUrl);
    List<CasRatingEntity> casRatings = dataMapper.mapToCasRatings(data, venueId, sourceUrl);

    int totalCount = jcrRatings.size() + casRatings.size();
    log.info(
        "Venue [id={}, issn={}] LetPub 富化成功，生成 {} 条评级（JCR:{}, CAS:{}）",
        venueId,
        issnL,
        totalCount,
        jcrRatings.size(),
        casRatings.size());

    return LetPubEnrichResult.of(venueId, jcrRatings, casRatings);
  }

  /// 构建 LetPub 详情页 URL，用于数据溯源。
  private String buildSourceUrl(String journalId) {
    if (journalId == null || journalId.isBlank()) {
      return null;
    }
    return "https://www.letpub.com.cn/index.php?journalid="
        + journalId
        + "&page=journalapp&view=detail";
  }
}
