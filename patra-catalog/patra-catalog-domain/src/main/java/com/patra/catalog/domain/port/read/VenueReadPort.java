package com.patra.catalog.domain.port.read;

import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueRatingHistoryReadModel;
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
}
