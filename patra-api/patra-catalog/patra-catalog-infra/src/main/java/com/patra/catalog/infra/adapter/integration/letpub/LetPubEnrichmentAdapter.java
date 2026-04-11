package com.patra.catalog.infra.adapter.integration.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// LetPub 富化适配器。
///
/// 实现 {@link LetPubEnrichmentPort} 领域端口，委托给 {@link LetPubScrapingClient} 执行爬取。
///
/// **错误处理策略**：
///
/// - 爬取异常由 Client 内部转为 {@link LetPubScrapingException}
/// - 本适配器不做额外异常处理，由 App 层 Worker/Runner 的异常路径统一处理
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class LetPubEnrichmentAdapter implements LetPubEnrichmentPort {

  private final LetPubScrapingClient letPubScrapingClient;

  /// {@inheritDoc}
  ///
  /// 委托给 {@link LetPubScrapingClient#findByIssn(String)} 执行两步爬取。
  @Override
  public Optional<LetPubVenueData> findByIssn(String issn) {
    if (issn == null || issn.isBlank()) {
      return Optional.empty();
    }
    return letPubScrapingClient.findByIssn(issn);
  }
}
