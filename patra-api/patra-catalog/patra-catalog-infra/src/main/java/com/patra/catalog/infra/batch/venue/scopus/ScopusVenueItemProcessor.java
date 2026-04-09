package com.patra.catalog.infra.batch.venue.scopus;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// Scopus 期刊富化 Processor。
///
/// 从 {@link ScopusEnrichmentPort} 按 ISSN-L 查询 Scopus 数据，
/// 然后通过 {@link ScopusDataMapper} 转换为评级实体列表。
///
/// **限速策略**：每次处理前 sleep 400ms（约 2.5 req/s），
/// 在 chunk=1 的配置下确保不超过 Scopus 免费额度的 2-3 req/s 限制。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class ScopusVenueItemProcessor implements ItemProcessor<VenueEntity, ScopusEnrichResult> {

  /// API 请求间隔（毫秒），控制在 2.5 req/s 以内。
  private static final long RATE_LIMIT_DELAY_MS = 400;

  private final ScopusEnrichmentPort enrichmentPort;
  private final ScopusDataMapper dataMapper;

  /// 处理单个 VenueEntity，通过 ISSN-L 查询 Scopus 数据并映射为评级行。
  @Override
  public ScopusEnrichResult process(VenueEntity item) throws Exception {
    String issnL = item.getIssnL();
    if (issnL == null || issnL.isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 Scopus 富化", item.getId());
      return null;
    }

    // 限速：确保不超过 Scopus 免费 API 限制
    Thread.sleep(RATE_LIMIT_DELAY_MS);

    Optional<ScopusVenueData> result = enrichmentPort.findByIssn(issnL);
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 Scopus 找到数据", item.getId(), issnL);
      return null;
    }

    ScopusVenueData data = result.get();
    Long venueId = item.getId();

    List<ScopusRatingEntity> scopusRatings = dataMapper.mapToScopusRatings(data, venueId);

    log.info("Venue [id={}, issn={}] Scopus 富化成功，生成 {} 条评级", venueId, issnL, scopusRatings.size());

    return ScopusEnrichResult.of(venueId, scopusRatings);
  }
}
