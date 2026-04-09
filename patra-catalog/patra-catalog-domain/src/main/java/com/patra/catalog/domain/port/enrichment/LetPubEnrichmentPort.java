package com.patra.catalog.domain.port.enrichment;

import java.util.Optional;

/// LetPub 期刊评价数据富化端口。
///
/// 定义从 [LetPub](https://www.letpub.com.cn) 获取期刊评价数据的能力。
/// 与 Wikidata/OpenAlex 的批量查询不同，LetPub 采用逐条 ISSN 搜索 + HTML 解析的方式，
/// 每次调用仅处理单条期刊。
///
/// **实现约束**：
///
/// - 每次 HTTP 请求需要 8-10 秒的延迟（反爬策略）
/// - 实现方需处理限流检测和指数退避重试
/// - 查询失败时返回 `Optional.empty()`，不抛出异常
///
/// @author linqibin
/// @since 0.1.0
public interface LetPubEnrichmentPort {

  /// 通过 ISSN 查询 LetPub 期刊评价数据。
  ///
  /// 执行两步查询：先通过 ISSN 搜索获取 LetPub 内部 ID，再爬取详情页提取评价数据。
  ///
  /// @param issn ISSN 标识符（通常为 ISSN-L）
  /// @return 期刊评价数据，未找到时返回 `Optional.empty()`
  Optional<LetPubVenueData> findByIssn(String issn);
}
