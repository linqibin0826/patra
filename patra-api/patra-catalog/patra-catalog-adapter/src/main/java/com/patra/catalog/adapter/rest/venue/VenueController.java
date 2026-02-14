package com.patra.catalog.adapter.rest.venue;

import com.patra.catalog.adapter.rest.venue.mapper.VenueApiConverter;
import com.patra.catalog.adapter.rest.venue.request.VenueListRequest;
import com.patra.catalog.adapter.rest.venue.response.VenueItemResponse;
import com.patra.catalog.app.usecase.venue.query.VenueQueryService;
import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.common.query.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Venue 查询控制器。
///
/// 提供面向前端管理台的 Venue 分页检索接口。
@Tag(name = "Venue", description = "期刊/会议场所查询")
@RestController
@RequestMapping("/venues")
@RequiredArgsConstructor
public class VenueController {

  private final VenueQueryService venueQueryService;
  private final VenueApiConverter venueApiConverter;

  /// 查询 Venue 分页列表。
  ///
  /// @param request Venue 列表查询请求（Spring MVC 自动绑定 query params）
  /// @return 分页响应
  @GetMapping
  public PageResult<VenueItemResponse> listVenues(VenueListRequest request) {
    VenueListQuery query = venueApiConverter.toQuery(request);
    return venueQueryService.listVenues(query).map(venueApiConverter::toItemResponse);
  }
}
