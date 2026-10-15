package dev.patra.catalog.adapter.rest.portal;

import dev.patra.catalog.adapter.rest.portal.request.PortalVenueListRequest;
import dev.patra.catalog.adapter.rest.portal.response.PortalVenueBrowseResponse;
import dev.patra.catalog.adapter.rest.portal.response.PortalVenueDetailResponse;
import dev.patra.catalog.adapter.rest.portal.response.PortalVenueFacetsResponse;
import dev.patra.catalog.app.usecase.portal.query.PortalVenueBrowseQueryService;
import dev.patra.catalog.app.usecase.portal.query.PortalVenueDetailQueryService;
import dev.patra.catalog.domain.model.read.portal.VenueBrowseFilter;
import dev.patra.catalog.domain.model.read.portal.VenueBrowseSort;
import dev.patra.catalog.domain.query.PageResult;
import dev.patra.catalog.domain.query.PagingParams;
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
