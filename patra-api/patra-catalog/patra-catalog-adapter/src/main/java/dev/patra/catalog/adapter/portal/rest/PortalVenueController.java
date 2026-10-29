package dev.patra.catalog.adapter.portal.rest;

import dev.patra.catalog.adapter.portal.rest.converter.PortalApiConverter;
import dev.patra.catalog.adapter.portal.rest.request.PortalVenueBatchRequest;
import dev.patra.catalog.adapter.portal.rest.request.PortalVenueListRequest;
import dev.patra.catalog.adapter.portal.rest.response.PortalVenueBatchResponse;
import dev.patra.catalog.adapter.portal.rest.response.PortalVenueBrowseResponse;
import dev.patra.catalog.adapter.portal.rest.response.PortalVenueDetailResponse;
import dev.patra.catalog.adapter.portal.rest.response.PortalVenueFacetsResponse;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueBrowseQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueCardQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueDetailQueryService;
import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.PagingParams;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFilter;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseSort;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Portal C 端门户期刊浏览/检索控制器。
///
/// @author linqibin
/// @since 0.1.0
@Tag(name = "Portal", description = "Portal C 端门户专用接口")
@Validated
@RestController
@RequestMapping("/portal/venues")
@RequiredArgsConstructor
public class PortalVenueController {

  /// 默认每页大小（portal 期刊浏览一屏密度）。
  private static final int DEFAULT_PAGE_SIZE = 12;

  /// 每页大小上限。
  private static final int MAX_PAGE_SIZE = 50;

  private final PortalVenueBrowseQueryService portalVenueBrowseQueryService;
  private final PortalVenueDetailQueryService portalVenueDetailQueryService;
  private final PortalVenueCardQueryService portalVenueCardQueryService;
  private final PortalApiConverter portalApiConverter;

  /// 浏览/检索期刊（支持多维度过滤和分页）。
  ///
  /// @param req 浏览请求（Spring MVC 自动绑定 query params）
  /// @return 分页期刊卡片列表
  @GetMapping
  public PageResult<PortalVenueBrowseResponse> browse(@Valid PortalVenueListRequest req) {
    PagingParams paging =
        PagingParams.normalize(req.page(), req.pageSize(), DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    VenueBrowseFilter filter = toFilter(req);
    return portalVenueBrowseQueryService
        .browse(filter, paging)
        .map(portalApiConverter::toVenueBrowseResponse);
  }

  /// 查询期刊各维度 facet 聚合计数（忽略 page/sort/pageSize 参数）。
  ///
  /// @param req 筛选请求（Spring MVC 自动绑定 query params，page/sort/pageSize 忽略）
  /// @return facet 聚合结果
  @GetMapping("/facets")
  public PortalVenueFacetsResponse facets(@Valid PortalVenueListRequest req) {
    VenueBrowseFilter filter = toFilter(req);
    return portalApiConverter.toVenueFacetsResponse(portalVenueBrowseQueryService.facets(filter));
  }

  /// 按 ID 批量取期刊卡片（重复参数 `id`，1 ~ 100 个）：不存在、软删与非期刊的不返回，按请求顺序。
  ///
  /// `/batch` 是字面路径，路径匹配优先于 `/{id}` 模板。
  ///
  /// @param req 批量请求
  /// @return 卡片，元素与浏览列表项同形
  @GetMapping("/batch")
  public PortalVenueBatchResponse batch(@Valid PortalVenueBatchRequest req) {
    return PortalVenueBatchResponse.of(
        portalVenueCardQueryService.findByIds(req.id()).stream()
            .map(portalApiConverter::toVenueBrowseResponse)
            .toList());
  }

  /// 查询期刊详情。
  ///
  /// @param id 期刊 ID（必须为正整数）
  /// @return 期刊详情
  @GetMapping("/{id}")
  public PortalVenueDetailResponse getVenueDetail(@PathVariable @Positive long id) {
    return portalApiConverter.toVenueDetailResponse(portalVenueDetailQueryService.getById(id));
  }

  /// 将请求 DTO 转为领域过滤参数。
  ///
  /// @param req 请求 DTO
  /// @return 领域过滤参数
  private VenueBrowseFilter toFilter(PortalVenueListRequest req) {
    return VenueBrowseFilter.builder()
        .keyword(blankToNull(req.q()))
        .sort(VenueBrowseSort.fromCode(req.sort()))
        .subjects(req.subject())
        .jcrQuartiles(req.jcr())
        .casQuartiles(req.cas())
        .casTop(req.casTop())
        .isOpenAccess(req.oa())
        .doaj(req.doaj())
        .countryCodes(req.country())
        .build();
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
