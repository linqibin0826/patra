package com.patra.catalog.domain.port.read;

import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;

/// Venue 读端口。
///
/// CQRS 读端驱动端口，提供面向查询场景的 Venue 分页读取能力。
public interface VenueReadPort {

  /// 查询 Venue 分页列表。
  ///
  /// @param paging 已验证的分页参数
  /// @param keyword 关键词（可空）
  /// @return Venue 分页结果
  PageResult<VenueSummaryReadModel> findVenuePage(PagingParams paging, String keyword);
}
