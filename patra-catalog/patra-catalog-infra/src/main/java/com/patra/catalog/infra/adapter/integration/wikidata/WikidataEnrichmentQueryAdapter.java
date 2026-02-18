package com.patra.catalog.infra.adapter.integration.wikidata;

import com.patra.catalog.domain.model.vo.venue.VenueWikidataEnrichment;
import com.patra.catalog.domain.port.enrichment.WikidataEnrichmentQueryPort;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// Wikidata 富化查询适配器。
///
/// 通过 Wikidata SPARQL API 单次查询获取期刊的中文标题和封面图片，
/// 实现 {@link WikidataEnrichmentQueryPort} 领域端口。
///
/// **错误处理策略**：
///
/// - Wikidata 服务不可用时返回空 Map，不影响主流程
/// - Wikidata 富化是可选的增强操作，失败不应阻断期刊导入
///
/// **委托关系**：直接委托给 {@link WikidataSparqlClient}，错误处理已在 Client 内完成。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class WikidataEnrichmentQueryAdapter implements WikidataEnrichmentQueryPort {

  private final WikidataSparqlClient wikidataSparqlClient;

  /// {@inheritDoc}
  ///
  /// 委托给 {@link WikidataSparqlClient#queryEnrichmentData(Set)} 执行查询，
  /// 空输入直接返回空 Map 跳过网络调用。
  @Override
  public Map<String, VenueWikidataEnrichment> findEnrichmentData(Set<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Map.of();
    }
    return wikidataSparqlClient.queryEnrichmentData(issnLs);
  }
}
