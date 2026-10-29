package dev.patra.catalog.adapter.rest.internal;

import dev.patra.catalog.api.venue.dto.VenueResolutionResp;
import dev.patra.catalog.api.venue.dto.VenueResolveByIdsReq;
import dev.patra.catalog.api.venue.dto.VenueResolveByNlmIdsReq;
import dev.patra.catalog.api.venue.dto.VenueResolveResp;
import dev.patra.catalog.api.venue.dto.VenueStateResp;
import dev.patra.catalog.api.venue.endpoint.VenueEndpoint;
import dev.patra.catalog.app.usecase.venue.query.VenueResolveQueryService;
import dev.patra.catalog.app.usecase.venue.query.dto.VenueResolution;
import dev.patra.catalog.domain.model.read.venue.VenueResolveReadModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// 期刊内部端点：`POST /_internal/venues/resolve/by-ids|by-nlm-ids`。
///
/// 权限：内部服务间调用（`/_internal` 路径，网关对外 403）。请求校验写在契约接口的参数上。
///
/// @author linqibin
/// @since 0.1.0
@RestController
@RequiredArgsConstructor
public class VenueEndpointImpl implements VenueEndpoint {

  private final VenueResolveQueryService venueResolveQueryService;

  @Override
  public VenueResolveResp resolveByIds(VenueResolveByIdsReq request) {
    return toResp(venueResolveQueryService.resolveByIds(request.ids()));
  }

  @Override
  public VenueResolveResp resolveByNlmIds(VenueResolveByNlmIdsReq request) {
    return toResp(venueResolveQueryService.resolveByNlmIds(request.nlmIds()));
  }

  /// 解析结果 → 响应。
  ///
  /// @param resolutions 解析结果
  /// @return 响应
  private static VenueResolveResp toResp(List<VenueResolution> resolutions) {
    return VenueResolveResp.of(resolutions.stream().map(VenueEndpointImpl::toItem).toList());
  }

  /// 单个解析结果 → 响应行。
  ///
  /// @param resolution 解析结果
  /// @return 响应行
  private static VenueResolutionResp toItem(VenueResolution resolution) {
    VenueResolveReadModel found = resolution.found();
    if (found == null) {
      return VenueResolutionResp.missing(resolution.key());
    }
    return VenueResolutionResp.builder()
        .key(resolution.key())
        .exists(true)
        .state(found.deleted() ? VenueStateResp.DELETED : VenueStateResp.ACTIVE)
        .id(found.id())
        .nlmId(found.nlmId())
        .title(found.title())
        .abbreviatedTitle(found.abbreviatedTitle())
        .build();
  }
}
