package com.patra.catalog.domain.port.enrichment;

import java.util.Map;
import java.util.Set;

/// 中文标题查询端口。
///
/// 批量查询外部数据源获取载体的中文标题。
///
/// **设计原则**：
///
/// - Domain 层定义接口，隐藏具体数据源实现（Wikidata SPARQL 等）
/// - Infrastructure 层负责实现查询逻辑和错误处理
/// - 查询失败不应阻断主流程，返回空 Map 即可
///
/// **使用场景**：
///
/// - PubMed 数据导入时，批量获取新建 Venue 的中文标题
/// - 通过 ISSN-L 作为匹配键关联外部数据源
///
/// @author linqibin
/// @since 0.1.0
public interface ChineseTitleQueryPort {

  /// 批量查询 ISSN-L 对应的中文标题。
  ///
  /// @param issnLs ISSN-L 集合（不能为 null）
  /// @return ISSN-L → 中文标题的映射，未找到的 ISSN-L 不包含在结果中；
  ///     查询失败时返回空 Map
  Map<String, String> findChineseTitles(Set<String> issnLs);
}
