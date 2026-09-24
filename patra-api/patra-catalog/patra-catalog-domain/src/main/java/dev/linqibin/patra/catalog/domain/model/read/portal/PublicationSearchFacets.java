package dev.linqibin.patra.catalog.domain.model.read.portal;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 文献检索 facet 聚合结果（CQRS 读端）。
///
/// 分组维度（years / types / evidence / languages）遵循 drill-down 语义：某维度的计数按
/// 「当前其他已选维度」计算，忽略本维度自身已选值。`evidence` 固定 6 项（含 UNKNOWN），按 rank 降序。
///
/// @param years 出版年份 facet，年份降序
/// @param types 文献类型 facet，计数降序
/// @param evidence 证据等级 facet，固定 6 项，值为
// [dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel] 名称
/// @param languages 语言基码 facet，计数降序
/// @param openAccess 忽略 oa 自身筛选、保留其他条件下 is_oa=true 的篇数
/// @param total 满足当前全部筛选的篇数
/// @param lastSyncedAt 全库最后采集时间（不随筛选变），空库为 null
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationSearchFacets(
    List<FacetCount> years,
    List<FacetCount> types,
    List<FacetCount> evidence,
    List<FacetCount> languages,
    long openAccess,
    long total,
    Instant lastSyncedAt) {

  /// 紧凑构造器：各 List 为 null 时替换为空不可变列表。
  public PublicationSearchFacets {
    years = years != null ? List.copyOf(years) : List.of();
    types = types != null ? List.copyOf(types) : List.of();
    evidence = evidence != null ? List.copyOf(evidence) : List.of();
    languages = languages != null ? List.copyOf(languages) : List.of();
  }
}
