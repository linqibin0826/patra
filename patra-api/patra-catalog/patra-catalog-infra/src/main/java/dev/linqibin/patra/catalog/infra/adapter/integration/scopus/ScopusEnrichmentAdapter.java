package dev.linqibin.patra.catalog.infra.adapter.integration.scopus;

import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusEnrichmentPort;
import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusVenueData;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// Scopus 期刊指标富化适配器。
///
/// 实现 {@link ScopusEnrichmentPort}，委托给 {@link ScopusApiClient} 完成 API 调用。
///
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
public class ScopusEnrichmentAdapter implements ScopusEnrichmentPort {

  private final ScopusApiClient scopusApiClient;

  @Override
  public Optional<ScopusVenueData> findByIssn(String issn) {
    return scopusApiClient.findByIssn(issn);
  }
}
