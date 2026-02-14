package com.patra.catalog.adapter.rest.venue.mapper;

import com.patra.catalog.adapter.rest.venue.request.VenueListRequest;
import com.patra.catalog.adapter.rest.venue.response.VenueItemResponse;
import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// Venue 查询 API 转换器。
///
/// 将 Adapter 层 DTO 与 Application/Domain 读模型进行转换。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VenueApiConverter {

  /// 将列表请求转换为应用层查询参数。
  ///
  /// @param request Venue 列表请求
  /// @return 应用层查询参数
  VenueListQuery toQuery(VenueListRequest request);

  /// 将 Venue 摘要读模型转换为 API 列表项响应。
  ///
  /// @param readModel Venue 摘要读模型
  /// @return API 列表项响应
  VenueItemResponse toItemResponse(VenueSummaryReadModel readModel);
}
