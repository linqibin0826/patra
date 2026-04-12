package com.patra.catalog.domain.port.read;

import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueInstanceSummaryReadModel;
import com.patra.catalog.domain.model.read.venue.VenueRatingHistoryReadModel;
import com.patra.catalog.domain.model.read.venue.VenueStatsReadModel;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.util.Optional;

/// Venue 读端口。
///
/// CQRS 读端驱动端口，提供面向查询场景的 Venue 分页读取能力。
public interface VenueReadPort {

  /// 查询 Venue 分页列表。
  ///
  /// @param paging 已验证的分页参数
  /// @param filter 筛选条件
  /// @return Venue 分页结果
  PageResult<VenueSummaryReadModel> findVenuePage(PagingParams paging, VenueFilter filter);

  /// 查询 Venue 详情。
  ///
  /// @param id 期刊主键 ID
  /// @return Venue 详情读模型，不存在时返回 Optional.empty()
  Optional<VenueDetailReadModel> findVenueDetail(Long id);

  /// 查询 Venue 评级历史（JCR/CAS/Scopus/预警）。
  ///
  /// 返回所有年份的评级记录，各列表按年份降序排列。
  ///
  /// @param venueId 期刊主键 ID
  /// @return 评级历史读模型（无数据时各列表为空）
  VenueRatingHistoryReadModel findVenueRatingHistory(Long venueId);

  /// 查询 Venue 年度发文统计。
  ///
  /// 返回所有年份的发文量、引用量和 OA 发文量，按年份降序排列。
  ///
  /// @param venueId 期刊主键 ID
  /// @return 发文统计读模型（无数据时列表为空）
  VenueStatsReadModel findVenueStats(Long venueId);

  /// 查询 Venue 实例（卷/期）分页列表。
  ///
  /// 按出版年份降序、卷号降序、期号降序排列。
  ///
  /// @param venueId 期刊主键 ID
  /// @param paging 已验证的分页参数
  /// @param year 出版年份过滤（可为 null，表示不过滤）
  /// @return Venue 实例分页结果
  PageResult<VenueInstanceSummaryReadModel> findVenueInstances(
      Long venueId, PagingParams paging, Integer year);
}
