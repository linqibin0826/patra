package com.patra.catalog.domain.port.enrichment;

import com.patra.catalog.domain.model.vo.venue.VenueOpenAlexEnrichment;
import java.util.Map;
import java.util.Set;

/// OpenAlex 富化查询端口。
///
/// 批量查询 OpenAlex Sources API 获取期刊的引用指标和年度统计数据，
/// 填充 `CitationMetrics`（引用指标快照）和 `VenuePublicationStats`（年度时序统计）。
///
/// **设计原则**：
///
/// - Domain 层定义接口，隐藏具体实现（OpenAlex REST API）
/// - Infrastructure 层负责实现查询逻辑和错误处理
/// - 查询失败不应阻断主流程，返回空 Map 即可
/// - 使用 `select` 参数最小化响应体，减少网络开销
///
/// **使用场景**：
///
/// - PubMed 数据导入时，批量富化新建/更新 Venue 的引用指标
/// - 通过 ISSN-L 作为匹配键关联 OpenAlex Sources
///
/// @author linqibin
/// @since 0.1.0
public interface OpenAlexEnrichmentQueryPort {

  /// 批量查询 ISSN-L 对应的 OpenAlex 富化数据（引用指标 + 年度统计）。
  ///
  /// @param issnLs ISSN-L 集合（不能为 null）
  /// @return ISSN-L → 富化数据的映射，未找到的 ISSN-L 不包含在结果中；
  ///     查询失败时返回空 Map
  Map<String, VenueOpenAlexEnrichment> findEnrichmentData(Set<String> issnLs);
}
