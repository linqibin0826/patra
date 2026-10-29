package dev.patra.catalog.adapter.rest.portal;

import dev.patra.catalog.adapter.rest.portal.response.PortalOrganizationSuggestionResponse;
import dev.patra.catalog.domain.model.read.organization.OrganizationSuggestion;
import dev.patra.catalog.domain.model.read.organization.OrganizationSummary;
import org.springframework.stereotype.Component;

/// 门户机构建议读模型 → 响应 DTO 转换器。
///
/// @author linqibin
/// @since 0.1.0
@Component
public class PortalOrganizationApiConverter {

  /// 将机构建议候选转为响应 DTO。
  ///
  /// @param suggestion 机构建议候选
  /// @return 响应 DTO
  public PortalOrganizationSuggestionResponse toOrganizationSuggestionResponse(
      OrganizationSuggestion suggestion) {
    OrganizationSummary organization = suggestion.organization();
    return PortalOrganizationSuggestionResponse.builder()
        .rorId(organization.rorId())
        .displayName(organization.displayName())
        .zhName(organization.zhName())
        .matchedName(suggestion.matchedName())
        .countryCode(organization.countryCode())
        .cityName(organization.cityName())
        .types(organization.types())
        .build();
  }
}
