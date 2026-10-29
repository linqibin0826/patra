package dev.patra.catalog.adapter.rest.internal;

import dev.patra.catalog.api.organization.dto.OrganizationResp;
import dev.patra.catalog.api.organization.endpoint.OrganizationEndpoint;
import dev.patra.catalog.app.usecase.organization.query.OrganizationQueryService;
import dev.patra.catalog.domain.model.read.organization.OrganizationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// 机构内部端点：`GET /_internal/organizations/{rorId}`。
///
/// 权限：内部服务间调用（`/_internal` 路径，网关对外 403）。不存在时由查询服务抛出
/// `OrganizationNotFoundException`，错误引擎映射为 404。
///
/// @author linqibin
/// @since 0.1.0
@RestController
@RequiredArgsConstructor
public class OrganizationEndpointImpl implements OrganizationEndpoint {

  private final OrganizationQueryService organizationQueryService;

  @Override
  public OrganizationResp getOrganization(String rorId) {
    OrganizationSummary summary = organizationQueryService.getByRorId(rorId);
    return OrganizationResp.builder()
        .rorId(summary.rorId())
        .displayName(summary.displayName())
        .zhName(summary.zhName())
        .countryCode(summary.countryCode())
        .types(summary.types())
        .build();
  }
}
