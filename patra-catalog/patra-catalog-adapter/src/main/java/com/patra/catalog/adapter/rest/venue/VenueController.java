package com.patra.catalog.adapter.rest.venue;

import com.patra.catalog.adapter.rest.publication.mapper.PublicationApiConverter;
import com.patra.catalog.adapter.rest.publication.response.PublicationItemResponse;
import com.patra.catalog.adapter.rest.venue.mapper.VenueApiConverter;
import com.patra.catalog.adapter.rest.venue.request.InstancePublicationListRequest;
import com.patra.catalog.adapter.rest.venue.request.VenueInstanceListRequest;
import com.patra.catalog.adapter.rest.venue.request.VenueListRequest;
import com.patra.catalog.adapter.rest.venue.response.VenueDetailResponse;
import com.patra.catalog.adapter.rest.venue.response.VenueInstanceItemResponse;
import com.patra.catalog.adapter.rest.venue.response.VenueItemResponse;
import com.patra.catalog.adapter.rest.venue.response.VenueRatingHistoryResponse;
import com.patra.catalog.adapter.rest.venue.response.VenueStatsResponse;
import com.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import com.patra.catalog.app.usecase.publication.query.dto.PublicationListQuery;
import com.patra.catalog.app.usecase.venue.query.VenueQueryService;
import com.patra.catalog.app.usecase.venue.query.dto.VenueCompareQuery;
import com.patra.catalog.app.usecase.venue.query.dto.VenueDetailQuery;
import com.patra.catalog.app.usecase.venue.query.dto.VenueInstanceListQuery;
import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.app.usecase.venue.query.dto.VenueRatingHistoryQuery;
import com.patra.catalog.app.usecase.venue.query.dto.VenueStatsQuery;
import com.patra.common.query.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  private final PublicationQueryService publicationQueryService;
  private final PublicationApiConverter publicationApiConverter;

  /// 查询 Venue 分页列表。
  ///
  /// @param request Venue 列表查询请求（Spring MVC 自动绑定 query params）
  /// @return 分页响应
  @GetMapping
  public PageResult<VenueItemResponse> listVenues(VenueListRequest request) {
    VenueListQuery query = venueApiConverter.toQuery(request);
    return venueQueryService.listVenues(query).map(venueApiConverter::toItemResponse);
  }

  /// 批量查询 Venue 详情用于对比（2~5 本期刊）。
  ///
  /// 不存在的 ID 会被静默忽略，仅返回查到的结果。
  ///
  /// @param ids 对比期刊 ID 列表（逗号分隔）
  /// @return Venue 详情响应列表
  @GetMapping("/compare")
  public List<VenueDetailResponse> compareVenues(@RequestParam List<Long> ids) {
    var query = VenueCompareQuery.of(ids);
    return venueQueryService.compareVenues(query).stream()
        .map(venueApiConverter::toDetailResponse)
        .toList();
  }

  /// 查询 Venue 详情。
  ///
  /// @param id 期刊主键 ID
  /// @return Venue 详情响应
  @GetMapping("/{id}")
  public VenueDetailResponse getVenueDetail(@PathVariable Long id) {
    VenueDetailQuery query = VenueDetailQuery.of(id);
    return venueApiConverter.toDetailResponse(venueQueryService.getVenueDetail(query));
  }

  /// 查询 Venue 评级历史（JCR/CAS/Scopus/预警）。
  ///
  /// 返回所有年份的评级记录，供前端趋势图展示。非分页接口。
  ///
  /// @param id 期刊主键 ID
  /// @return 评级历史响应
  @GetMapping("/{id}/ratings")
  public VenueRatingHistoryResponse getVenueRatingHistory(@PathVariable Long id) {
    var query = VenueRatingHistoryQuery.of(id);
    return venueApiConverter.toRatingHistoryResponse(
        venueQueryService.getVenueRatingHistory(query));
  }

  /// 查询 Venue 年度发文统计。
  ///
  /// 返回所有年份的发文量、引用量和 OA 发文量，供前端趋势图展示。非分页接口。
  ///
  /// @param id 期刊主键 ID
  /// @return 发文统计响应
  @GetMapping("/{id}/stats")
  public VenueStatsResponse getVenueStats(@PathVariable Long id) {
    var query = VenueStatsQuery.of(id);
    return venueApiConverter.toStatsResponse(venueQueryService.getVenueStats(query));
  }

  /// 查询 Venue 实例（卷/期）分页列表。
  ///
  /// 支持按出版年份过滤，结果按 publicationYear DESC、volume DESC、issue DESC 排序。
  ///
  /// @param id 期刊主键 ID
  /// @param request 实例列表查询请求（Spring MVC 自动绑定 query params）
  /// @return 分页响应
  @GetMapping("/{id}/instances")
  public PageResult<VenueInstanceItemResponse> listVenueInstances(
      @PathVariable Long id, VenueInstanceListRequest request) {
    var query = VenueInstanceListQuery.of(id, request.year(), request.page(), request.pageSize());
    return venueQueryService
        .listVenueInstances(query)
        .map(venueApiConverter::toInstanceItemResponse);
  }

  /// 查询 Venue 实例下的文献分页列表。
  ///
  /// 支持按被引次数排序（sortBy=citedByCount），默认按更新时间降序。
  ///
  /// @param venueId 期刊主键 ID
  /// @param instanceId 实例主键 ID
  /// @param request 文献列表查询请求（Spring MVC 自动绑定 query params）
  /// @return 分页响应
  @GetMapping("/{venueId}/instances/{instanceId}/publications")
  public PageResult<PublicationItemResponse> listInstancePublications(
      @PathVariable Long venueId,
      @PathVariable Long instanceId,
      InstancePublicationListRequest request) {
    PublicationListQuery query =
        PublicationListQuery.builder()
            .page(request.page())
            .pageSize(request.pageSize())
            .venueId(venueId)
            .venueInstanceId(instanceId)
            .sortBy(request.sortBy())
            .build();
    return publicationQueryService
        .listPublications(query)
        .map(publicationApiConverter::toItemResponse);
  }
}
