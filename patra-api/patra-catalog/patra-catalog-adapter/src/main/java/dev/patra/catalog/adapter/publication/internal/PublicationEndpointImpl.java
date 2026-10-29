package dev.patra.catalog.adapter.publication.internal;

import dev.patra.catalog.api.publication.dto.PublicationResolutionResp;
import dev.patra.catalog.api.publication.dto.PublicationResolveByIdsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveByPmidsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveResp;
import dev.patra.catalog.api.publication.dto.PublicationStateResp;
import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.catalog.app.publication.usecase.query.PublicationResolution;
import dev.patra.catalog.app.publication.usecase.query.PublicationResolveQueryService;
import dev.patra.catalog.domain.publication.model.read.PublicationResolveReadModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// 文献内部端点：`POST /_internal/publications/resolve/by-ids|by-pmids`。
///
/// 权限：内部服务间调用（`/_internal` 路径，网关对外 403）。请求校验写在契约接口的参数上。
///
/// @author linqibin
/// @since 0.1.0
@RestController
@RequiredArgsConstructor
public class PublicationEndpointImpl implements PublicationEndpoint {

  private final PublicationResolveQueryService publicationResolveQueryService;

  @Override
  public PublicationResolveResp resolveByIds(PublicationResolveByIdsReq request) {
    return toResp(publicationResolveQueryService.resolveByIds(request.ids()));
  }

  @Override
  public PublicationResolveResp resolveByPmids(PublicationResolveByPmidsReq request) {
    return toResp(publicationResolveQueryService.resolveByPmids(request.pmids()));
  }

  /// 解析结果 → 响应。
  ///
  /// @param resolutions 解析结果
  /// @return 响应
  private static PublicationResolveResp toResp(List<PublicationResolution> resolutions) {
    return PublicationResolveResp.of(
        resolutions.stream().map(PublicationEndpointImpl::toItem).toList());
  }

  /// 单个解析结果 → 响应行。
  ///
  /// @param resolution 解析结果
  /// @return 响应行
  private static PublicationResolutionResp toItem(PublicationResolution resolution) {
    PublicationResolveReadModel found = resolution.found();
    if (found == null) {
      return PublicationResolutionResp.missing(resolution.key());
    }
    return PublicationResolutionResp.builder()
        .key(resolution.key())
        .exists(true)
        .state(found.deleted() ? PublicationStateResp.DELETED : PublicationStateResp.ACTIVE)
        .id(found.id())
        .pmid(found.pmid())
        .title(found.title())
        .journal(found.venueName())
        .year(found.publicationYear())
        .publishedDate(found.publishedDate())
        .build();
  }
}
