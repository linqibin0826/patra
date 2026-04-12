package com.patra.catalog.domain.model.read.venue;

import lombok.Builder;

/// Venue 列表查询筛选条件。
///
/// 封装 Venue 分页查询的所有筛选参数，所有字段均可为 null（表示不筛选）。
///
/// @param keyword title 前缀模糊搜索（可空）
/// @param countryCode 国家编码精确匹配（可空）
/// @param issnL ISSN-L 精确匹配（可空）
/// @param nlmId NLM ID 精确匹配（可空）
/// @param jifQuartile JCR JIF 分区过滤，如 Q1/Q2/Q3/Q4（可空）
/// @param casMajorQuartile CAS 大类分区过滤，如 1区/2区/3区/4区（可空）
/// @param casTopJournal 是否仅查 CAS Top 期刊（可空，true 表示仅 Top）
/// @param oaType OA 类型过滤，如 gold/hybrid/bronze（可空）
/// @param collection JCR 收录集过滤，如 SCIE/SSCI/AHCI（可空）
/// @param researchDirection 研究方向关键词匹配（可空）
/// @param warningOnly 是否仅查预警期刊（可空，true 表示仅预警）
/// @param sortBy 排序字段：impactFactor/citeScore/hIndex/citedByCount（可空，默认 citedByCount）
@Builder
public record VenueFilter(
    String keyword,
    String countryCode,
    String issnL,
    String nlmId,
    String jifQuartile,
    String casMajorQuartile,
    Boolean casTopJournal,
    String oaType,
    String collection,
    String researchDirection,
    Boolean warningOnly,
    String sortBy) {

  /// 判断是否包含需要 JOIN 评级表的高级筛选条件或非默认排序。
  ///
  /// @return true 如果存在高级筛选或非默认排序
  public boolean hasAdvancedFilters() {
    return jifQuartile != null
        || casMajorQuartile != null
        || Boolean.TRUE.equals(casTopJournal)
        || oaType != null
        || collection != null
        || researchDirection != null
        || Boolean.TRUE.equals(warningOnly)
        || sortBy != null;
  }
}
