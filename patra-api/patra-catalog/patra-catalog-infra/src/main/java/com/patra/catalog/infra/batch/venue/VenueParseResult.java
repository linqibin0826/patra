package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.ApcInfo;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueDetail;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.model.vo.venue.VenueStats;
import java.util.List;

/// OpenAlex Source 解析结果。
///
/// **职责**：
///
/// 封装解析后的 VenueAggregate 及其关联的补充数据。
/// 由于 CQRS 最小聚合设计，聚合根只包含核心字段（id/type/displayName/identifiers/provenance），
/// 其他数据通过此记录一并传递。
///
/// **数据结构**：
///
/// | 字段 | 说明 | 存储目标 |
/// |------|------|----------|
/// | aggregate | 核心聚合根 | cat_venue + cat_venue_identifier |
/// | detail | 详情值对象 | cat_venue_detail |
/// | stats | 统计快照 | cat_venue_stats |
/// | apcInfo | APC 费用信息 | cat_venue_apc |
/// | societies | 关联学会 | cat_venue_society |
/// | yearlyMetrics | 年度指标 | cat_venue_publication_stats |
///
/// **使用场景**：
///
/// - `OpenAlexSourceParser` 返回此类型
/// - `VenueInitializeItemReader` 传递此类型给 ItemWriter
/// - Application 层分别保存聚合根和补充数据
///
/// @param aggregate 解析后的 VenueAggregate 聚合根（必填）
/// @param detail 详情值对象（可选）
/// @param stats 统计快照（可选）
/// @param apcInfo APC 费用信息（可选）
/// @param societies 关联学会列表（可为空列表）
/// @param yearlyMetrics 关联的年度指标列表（可为空列表）
/// @author linqibin
/// @since 0.1.0
public record VenueParseResult(
    VenueAggregate aggregate,
    VenueDetail detail,
    VenueStats stats,
    ApcInfo apcInfo,
    List<Society> societies,
    List<VenuePublicationStats> yearlyMetrics) {

  /// 创建解析结果。
  ///
  /// @param aggregate 聚合根（不能为 null）
  /// @param detail 详情值对象（null 会被转换为空 VenueDetail）
  /// @param stats 统计快照（可为 null）
  /// @param apcInfo APC 信息（可为 null）
  /// @param societies 学会列表（null 会被转换为空列表）
  /// @param yearlyMetrics 年度指标（null 会被转换为空列表）
  public VenueParseResult {
    if (aggregate == null) {
      throw new IllegalArgumentException("aggregate 不能为 null");
    }
    if (detail == null) {
      detail = VenueDetail.empty();
    }
    if (societies == null) {
      societies = List.of();
    }
    if (yearlyMetrics == null) {
      yearlyMetrics = List.of();
    }
  }

  /// 是否有详情数据。
  ///
  /// @return true 如果详情不为空
  public boolean hasDetail() {
    return detail != null && !detail.equals(VenueDetail.empty());
  }

  /// 是否有统计快照。
  ///
  /// @return true 如果存在统计数据
  public boolean hasStats() {
    return stats != null;
  }

  /// 是否有 APC 信息。
  ///
  /// @return true 如果存在 APC 信息
  public boolean hasApcInfo() {
    return apcInfo != null;
  }

  /// 是否有关联学会。
  ///
  /// @return true 如果存在关联学会
  public boolean hasSocieties() {
    return !societies.isEmpty();
  }

  /// 是否有年度指标数据。
  ///
  /// @return true 如果存在年度指标
  public boolean hasYearlyMetrics() {
    return !yearlyMetrics.isEmpty();
  }
}
