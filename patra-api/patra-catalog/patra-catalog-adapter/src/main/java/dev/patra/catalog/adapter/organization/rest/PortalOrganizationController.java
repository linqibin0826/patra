package dev.patra.catalog.adapter.organization.rest;

import dev.patra.catalog.adapter.kernel.rest.request.PortalSuggestRequest;
import dev.patra.catalog.adapter.kernel.rest.response.PortalSuggestionsResponse;
import dev.patra.catalog.adapter.organization.rest.converter.PortalOrganizationApiConverter;
import dev.patra.catalog.adapter.organization.rest.response.PortalOrganizationSuggestionResponse;
import dev.patra.catalog.app.organization.usecase.query.OrganizationQueryService;
import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Portal C 端门户机构建议控制器：个人资料里选机构时边打边搜。
///
/// @author linqibin
/// @since 0.1.0
@Tag(name = "Portal", description = "Portal C 端门户专用接口")
@Validated
@RestController
@RequestMapping("/portal/organizations")
@RequiredArgsConstructor
public class PortalOrganizationController {

  private final OrganizationQueryService organizationQueryService;
  private final PortalOrganizationApiConverter portalOrganizationApiConverter;

  /// 按中文名、英文名、别名、缩写搜机构（ROR），中国机构优先。
  ///
  /// @param req 建议请求（Spring MVC 自动绑定 query params）
  /// @return 候选列表信封，没有命中为空数组
  @GetMapping("/suggest")
  public PortalSuggestionsResponse<PortalOrganizationSuggestionResponse> suggest(
      @Valid PortalSuggestRequest req) {
    SuggestQuery query = SuggestQuery.of(req.q(), req.limitOrDefault());
    List<PortalOrganizationSuggestionResponse> items =
        organizationQueryService.suggest(query).stream()
            .map(portalOrganizationApiConverter::toOrganizationSuggestionResponse)
            .toList();
    return PortalSuggestionsResponse.of(items);
  }
}
