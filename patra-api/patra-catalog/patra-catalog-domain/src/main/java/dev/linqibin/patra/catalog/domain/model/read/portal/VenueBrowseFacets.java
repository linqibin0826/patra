package dev.linqibin.patra.catalog.domain.model.read.portal;

import java.util.List;
import lombok.Builder;

/// 期刊浏览 facet 聚合结果（CQRS 读端）。
///
/// 每个分组维度返回 [FacetCount] 列表，其计数遵循 drill-down 语义：
/// 某维度的每个选项计数按「当前 query + 除本维度外其他已选维度」计算，
/// 组内多选不互相清零（即选 Q1 后仍可看到 Q2 的候选数量）。
///
/// @param subjects JCR 学科 facet 列表
/// @param jcrQuartiles JCR 分区 facet 列表（Q1-Q4）
/// @param casQuartiles CAS 分区 facet 列表（Q1-Q4）
/// @param countries 国家/地区 facet 列表
/// @param casTop 满足当前 query 且 casTop=true 的期刊数
/// @param openAccess 满足当前 query 且 isOpenAccess=true 的期刊数
/// @param doaj 满足当前 query 且 doaj=true 的期刊数
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueBrowseFacets(
    List<FacetCount> subjects,
    List<FacetCount> jcrQuartiles,
    List<FacetCount> casQuartiles,
    List<FacetCount> countries,
    long casTop,
    long openAccess,
    long doaj) {

  /// 紧凑构造器：各 List 为 null 时替换为空不可变列表。
  public VenueBrowseFacets {
    subjects = subjects != null ? List.copyOf(subjects) : List.of();
    jcrQuartiles = jcrQuartiles != null ? List.copyOf(jcrQuartiles) : List.of();
    casQuartiles = casQuartiles != null ? List.copyOf(casQuartiles) : List.of();
    countries = countries != null ? List.copyOf(countries) : List.of();
  }
}
