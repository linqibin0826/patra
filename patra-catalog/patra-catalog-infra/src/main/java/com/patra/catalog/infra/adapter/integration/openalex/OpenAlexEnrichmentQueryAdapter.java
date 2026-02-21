package com.patra.catalog.infra.adapter.integration.openalex;

import com.patra.catalog.domain.model.vo.venue.VenueOpenAlexEnrichment;
import com.patra.catalog.domain.port.enrichment.OpenAlexEnrichmentQueryPort;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// OpenAlex 富化查询适配器。
///
/// 通过 OpenAlex Sources API 查询获取期刊的引用指标和年度统计数据，
/// 实现 {@link OpenAlexEnrichmentQueryPort} 领域端口。
///
/// **错误处理策略**：
///
/// - OpenAlex 服务不可用时返回空 Map，不影响主流程
/// - OpenAlex 富化是可选的增强操作，失败不应阻断期刊导入
///
/// **委托关系**：直接委托给 {@link OpenAlexSourcesClient}，错误处理已在 Client 内完成。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAlexEnrichmentQueryAdapter implements OpenAlexEnrichmentQueryPort {

  private final OpenAlexSourcesClient openAlexSourcesClient;

  /// {@inheritDoc}
  ///
  /// 委托给 {@link OpenAlexSourcesClient#queryEnrichmentData(Set)} 执行查询，
  /// 空输入直接返回空 Map 跳过网络调用。
  @Override
  public Map<String, VenueOpenAlexEnrichment> findEnrichmentData(Set<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Map.of();
    }
    return openAlexSourcesClient.queryEnrichmentData(issnLs);
  }
}
