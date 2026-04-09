package com.patra.catalog.domain.port.enrichment;

import java.util.Optional;

/// Scopus 期刊指标数据富化端口。
///
/// 定义从 [Scopus Serial Title API](https://dev.elsevier.com/) 获取期刊评价数据的能力。
/// 与 LetPub 的爬虫模式不同，Scopus 采用正规 REST API + API Key 认证，
/// 每次调用仅处理单条期刊（按 ISSN 查询）。
///
/// **实现约束**：
///
/// - 免费额度：~20,000 次/周，2-3 次/秒
/// - 实现方需通过 `X-ELS-APIKey` 请求头传递 API Key
/// - 查询失败时返回 `Optional.empty()`，不抛出异常
///
/// @author linqibin
/// @since 0.1.0
public interface ScopusEnrichmentPort {

  /// 通过 ISSN 查询 Scopus 期刊指标数据。
  ///
  /// 调用 Serial Title API 获取 CiteScore、SJR、SNIP 等指标及历史趋势数据。
  ///
  /// @param issn ISSN 标识符（通常为 ISSN-L）
  /// @return 期刊指标数据，未找到时返回 `Optional.empty()`
  Optional<ScopusVenueData> findByIssn(String issn);
}
