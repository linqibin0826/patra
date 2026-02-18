package com.patra.catalog.domain.port.enrichment;

import com.patra.catalog.domain.model.vo.venue.VenueWikidataEnrichment;
import java.util.Map;
import java.util.Set;

/// Wikidata 富化查询端口。
///
/// 批量查询 Wikidata 获取期刊的富化数据（中文标题 + 封面图片 + 官方网站），
/// 替代原来仅查询中文标题的 `ChineseTitleQueryPort`。
///
/// **设计原则**：
///
/// - Domain 层定义接口，隐藏具体实现（Wikidata SPARQL）
/// - Infrastructure 层负责实现查询逻辑和错误处理
/// - 查询失败不应阻断主流程，返回空 Map 即可
/// - 单次 SPARQL 调用同时获取中文标题、封面图片和官方网站，避免多次网络往返
///
/// **使用场景**：
///
/// - PubMed 数据导入时，批量富化新建/更新 Venue 的 Wikidata 数据
/// - 通过 ISSN-L 作为匹配键关联外部数据源
///
/// @author linqibin
/// @since 0.1.0
public interface WikidataEnrichmentQueryPort {

  /// 批量查询 ISSN-L 对应的 Wikidata 富化数据（中文标题 + 封面图片 + 官方网站）。
  ///
  /// @param issnLs ISSN-L 集合（不能为 null）
  /// @return ISSN-L → 富化数据的映射，未找到的 ISSN-L 不包含在结果中；
  ///     查询失败时返回空 Map
  Map<String, VenueWikidataEnrichment> findEnrichmentData(Set<String> issnLs);
}
