package com.patra.catalog.infra.adapter.integration.wikidata;

import com.patra.catalog.domain.port.enrichment.ChineseTitleQueryPort;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// Wikidata 中文标题查询适配器。
///
/// 通过 Wikidata SPARQL API 查询期刊的中文标题，
/// 实现 {@link ChineseTitleQueryPort} 领域端口。
///
/// **错误处理策略**：
///
/// - Wikidata 服务不可用时返回空 Map，不影响主流程
/// - 中文标题富化是可选的增强操作，失败不应阻断期刊导入
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class WikidataChineseTitleQueryAdapter implements ChineseTitleQueryPort {

  private final WikidataSparqlClient wikidataSparqlClient;

  @Override
  public Map<String, String> findChineseTitles(Set<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Map.of();
    }

    try {
      return wikidataSparqlClient.queryChineseTitles(issnLs);
    } catch (Exception ex) {
      log.warn("Wikidata 中文标题查询失败，返回空结果: {}", ex.getMessage());
      return Map.of();
    }
  }
}
