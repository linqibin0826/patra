package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.model.vo.venue.LetPubVenueData;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// LetPub 期刊富化 Processor。
///
/// 从 {@link LetPubEnrichmentPort} 按 ISSN-L 查询 LetPub 期刊评价数据。
/// 返回 null 表示跳过该条目（Spring Batch 约定）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemProcessor implements ItemProcessor<VenueEntity, LetPubEnrichResult> {

  private final LetPubEnrichmentPort enrichmentPort;

  /// 处理单个 VenueEntity，通过 ISSN-L 查询 LetPub 数据。
  ///
  /// - ISSN-L 为空时直接跳过
  /// - LetPub 未找到数据时返回 null（跳过）
  /// - 查到数据时封装为 {@link LetPubEnrichResult}
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

    log.info("Venue [id={}, issn={}] LetPub 富化成功", item.getId(), issnL);
    return LetPubEnrichResult.of(item.getId(), result.get());
  }
}
