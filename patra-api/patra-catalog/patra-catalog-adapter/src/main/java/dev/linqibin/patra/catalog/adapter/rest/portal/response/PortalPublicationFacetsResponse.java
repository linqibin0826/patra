package dev.linqibin.patra.catalog.adapter.rest.portal.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// Portal 文献 facets 响应 DTO，镜像领域读模型
/// [dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFacets]，字段名对齐前端零映射。
///
/// @param years 年份 facet，年份降序
/// @param types 文献类型 facet，计数降序
/// @param evidence 证据等级 facet，固定 6 项（rank 降序，UNKNOWN 末尾）
/// @param languages 语言基码 facet，计数降序
/// @param openAccess 忽略 oa 自身筛选、保留其他条件下的 OA 篇数
/// @param total 满足当前全部筛选的篇数
/// @param lastSyncedAt 全库最后采集时间，空库为 null
/// @author linqibin
/// @since 0.1.0
@Builder
public record PortalPublicationFacetsResponse(
    List<FacetCountResponse> years,
    List<FacetCountResponse> types,
    List<FacetCountResponse> evidence,
    List<FacetCountResponse> languages,
    long openAccess,
    long total,
    Instant lastSyncedAt) {

  /// 紧凑构造器：各 List 为 null 时替换为空不可变列表。
  public PortalPublicationFacetsResponse {
    years = years != null ? List.copyOf(years) : List.of();
    types = types != null ? List.copyOf(types) : List.of();
    evidence = evidence != null ? List.copyOf(evidence) : List.of();
    languages = languages != null ? List.copyOf(languages) : List.of();
  }
}
