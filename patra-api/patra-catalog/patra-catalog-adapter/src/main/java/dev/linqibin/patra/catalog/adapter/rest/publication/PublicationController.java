package dev.linqibin.patra.catalog.adapter.rest.publication;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.patra.catalog.adapter.rest.publication.mapper.PublicationApiConverter;
import dev.linqibin.patra.catalog.adapter.rest.publication.request.PublicationListRequest;
import dev.linqibin.patra.catalog.adapter.rest.publication.response.PublicationDetailResponse;
import dev.linqibin.patra.catalog.adapter.rest.publication.response.PublicationItemResponse;
import dev.linqibin.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import dev.linqibin.patra.catalog.app.usecase.publication.query.dto.PublicationDetailQuery;
import dev.linqibin.patra.catalog.app.usecase.publication.query.dto.PublicationListQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Publication 查询控制器。
///
/// 提供面向前端管理台的 Publication 分页检索和详情查询接口。
@Tag(name = "Publication", description = "文献出版物查询")
@RestController
@RequestMapping("/publications")
@RequiredArgsConstructor
public class PublicationController {

  private final PublicationQueryService publicationQueryService;
  private final PublicationApiConverter publicationApiConverter;

  /// 查询 Publication 分页列表。
  ///
  /// @param request Publication 列表查询请求（Spring MVC 自动绑定 query params）
  /// @return 分页响应
  @GetMapping
  public PageResult<PublicationItemResponse> listPublications(PublicationListRequest request) {
    PublicationListQuery query = publicationApiConverter.toQuery(request);
    return publicationQueryService
        .listPublications(query)
        .map(publicationApiConverter::toItemResponse);
  }

  /// 查询 Publication 详情。
  ///
  /// @param id 文献主键 ID
  /// @return Publication 详情响应
  @GetMapping("/{id}")
  public PublicationDetailResponse getPublicationDetail(@PathVariable Long id) {
    PublicationDetailQuery query = PublicationDetailQuery.of(id);
    return publicationApiConverter.toDetailResponse(
        publicationQueryService.getPublicationDetail(query));
  }
}
