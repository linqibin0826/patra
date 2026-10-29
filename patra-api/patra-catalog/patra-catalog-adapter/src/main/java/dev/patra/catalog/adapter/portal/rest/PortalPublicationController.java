package dev.patra.catalog.adapter.portal.rest;

import dev.patra.catalog.adapter.portal.rest.converter.PortalApiConverter;
import dev.patra.catalog.adapter.portal.rest.request.PortalPublicationBatchRequest;
import dev.patra.catalog.adapter.portal.rest.request.PortalPublicationListRequest;
import dev.patra.catalog.adapter.portal.rest.request.PortalPublicationSearchRequest;
import dev.patra.catalog.adapter.portal.rest.request.RepeatedParamListEditor;
import dev.patra.catalog.adapter.portal.rest.response.PortalPaperBatchResponse;
import dev.patra.catalog.adapter.portal.rest.response.PortalPaperResponse;
import dev.patra.catalog.adapter.portal.rest.response.PortalPublicationDetailResponse;
import dev.patra.catalog.adapter.portal.rest.response.PortalPublicationFacetsResponse;
import dev.patra.catalog.app.portal.usecase.query.PortalFeedQuery;
import dev.patra.catalog.app.portal.usecase.query.PortalFeedQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalPublicationCardQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalPublicationDetailQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalPublicationSearchQueryService;
import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.PagingParams;
import dev.patra.catalog.domain.portal.model.read.PublicationSearchFilter;
import dev.patra.catalog.domain.portal.model.read.PublicationSearchSort;
import dev.patra.catalog.domain.portal.model.vo.EvidenceLevel;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Portal C 端门户文献流 / 文献检索控制器。
///
/// @author linqibin
/// @since 0.1.0
@Tag(name = "Portal", description = "Portal C 端门户专用接口")
@Validated
@RestController
@RequestMapping("/portal/publications")
@RequiredArgsConstructor
public class PortalPublicationController {

  /// 检索端点默认每页大小。
  private static final int SEARCH_DEFAULT_PAGE_SIZE = 20;

  /// 检索端点每页大小上限。
  private static final int SEARCH_MAX_PAGE_SIZE = 50;

  private final PortalFeedQueryService portalFeedQueryService;
  private final PortalPublicationDetailQueryService portalPublicationDetailQueryService;
  private final PortalPublicationSearchQueryService portalPublicationSearchQueryService;
  private final PortalPublicationCardQueryService portalPublicationCardQueryService;
  private final PortalApiConverter portalApiConverter;

  /// 本控制器的 `List` 参数全部按重复参数绑定、不拆逗号（类型值含逗号，见 [RepeatedParamListEditor]）。
  ///
  /// @param binder 当前请求的数据绑定器
  @InitBinder
  void registerRepeatedParamLists(WebDataBinder binder) {
    binder.registerCustomEditor(List.class, new RepeatedParamListEditor());
  }

  /// 查询 portal 文献流分页列表。
  ///
  /// @param request 查询请求（Spring MVC 自动绑定 query params）
  /// @return 分页响应
  @GetMapping
  public PageResult<PortalPaperResponse> listFeed(@Valid PortalPublicationListRequest request) {
    PortalFeedQuery query = PortalFeedQuery.of(request.tab(), request.page(), request.pageSize());
    return portalFeedQueryService.listFeed(query).map(portalApiConverter::toResponse);
  }

  /// 检索文献（关键词 / 作者 / PMID / DOI / 六类筛选 / 两种排序 / 分页）。
  ///
  /// @param req 检索请求（Spring MVC 自动绑定 query params）
  /// @return 分页文献列表（与 feed 同形）
  @GetMapping("/search")
  public PageResult<PortalPaperResponse> search(@Valid PortalPublicationSearchRequest req) {
    PagingParams paging =
        PagingParams.normalize(
            req.page(), req.pageSize(), SEARCH_DEFAULT_PAGE_SIZE, SEARCH_MAX_PAGE_SIZE);
    return portalPublicationSearchQueryService
        .search(toFilter(req), paging)
        .map(portalApiConverter::toResponse);
  }

  /// 检索 facets 计数（忽略 sort / page / pageSize）。
  ///
  /// @param req 检索请求
  /// @return facets 聚合结果
  @GetMapping("/search/facets")
  public PortalPublicationFacetsResponse facets(@Valid PortalPublicationSearchRequest req) {
    return portalApiConverter.toPublicationFacetsResponse(
        portalPublicationSearchQueryService.facets(toFilter(req)));
  }

  /// 按 ID 批量取文献卡片（文献库、收藏夹页）。不存在与软删的 ID 不返回。
  ///
  /// `/batch` 是字面路径，优先于 `/{id}` 模板匹配。
  ///
  /// @param request 文献 ID 列表（重复参数 `id`）
  /// @return 卡片，按请求顺序
  @GetMapping("/batch")
  public PortalPaperBatchResponse batch(@Valid PortalPublicationBatchRequest request) {
    return PortalPaperBatchResponse.of(
        portalPublicationCardQueryService.findByIds(request.id()).stream()
            .map(portalApiConverter::toResponse)
            .toList());
  }

  /// 查询文献详情。
  ///
  /// @param id 文献 ID（必须为正整数）
  /// @return 文献详情
  @GetMapping("/{id}")
  public PortalPublicationDetailResponse getDetail(@PathVariable @Positive long id) {
    return portalApiConverter.toPublicationDetailResponse(
        portalPublicationDetailQueryService.getById(id));
  }

  /// 请求 DTO → 领域过滤参数。LIKE 转义与 DOI 归一化在读适配器做。
  ///
  /// @param req 请求 DTO
  /// @return 领域过滤参数
  private PublicationSearchFilter toFilter(PortalPublicationSearchRequest req) {
    return PublicationSearchFilter.builder()
        .keyword(blankToNull(req.q()))
        .author(blankToNull(req.author()))
        .pmid(blankToNull(req.pmid()))
        .doi(blankToNull(req.doi()))
        .yearFrom(req.yearFrom())
        .yearTo(req.yearTo())
        .venueIds(req.venue())
        .types(req.type())
        .evidenceLevels(
            req.evidence().stream()
                .map(e -> EvidenceLevel.valueOf(e.toUpperCase(Locale.ROOT)))
                .toList())
        .isOpenAccess(req.oa())
        .languages(req.lang())
        .sort(PublicationSearchSort.fromCode(req.sort()))
        .build();
  }

  /// 空白 → null，否则 trim。
  ///
  /// @param value 原始字符串
  /// @return trim 后的字符串或 null
  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
