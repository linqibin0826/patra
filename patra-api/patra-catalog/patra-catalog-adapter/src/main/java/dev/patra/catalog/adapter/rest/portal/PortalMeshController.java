package dev.patra.catalog.adapter.rest.portal;

import dev.patra.catalog.adapter.rest.portal.request.PortalSuggestRequest;
import dev.patra.catalog.adapter.rest.portal.response.PortalMeshSuggestionResponse;
import dev.patra.catalog.adapter.rest.portal.response.PortalSuggestionsResponse;
import dev.patra.catalog.app.usecase.mesh.query.MeshDescriptorQueryService;
import dev.patra.catalog.domain.query.SuggestQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Portal C 端门户 MeSH 建议控制器：个人资料里选关注领域时边打边搜。
///
/// @author linqibin
/// @since 0.1.0
@Tag(name = "Portal", description = "Portal C 端门户专用接口")
@Validated
@RestController
@RequestMapping("/portal/mesh")
@RequiredArgsConstructor
public class PortalMeshController {

  private final MeshDescriptorQueryService meshDescriptorQueryService;
  private final PortalMeshApiConverter portalMeshApiConverter;

  /// 按主题词英文名与入口词搜 MeSH 主题词，经入口词命中的返回主题词本身。
  ///
  /// @param req 建议请求（Spring MVC 自动绑定 query params）
  /// @return 候选列表信封，没有命中为空数组
  @GetMapping("/suggest")
  public PortalSuggestionsResponse<PortalMeshSuggestionResponse> suggest(
      @Valid PortalSuggestRequest req) {
    SuggestQuery query = SuggestQuery.of(req.q(), req.limitOrDefault());
    List<PortalMeshSuggestionResponse> items =
        meshDescriptorQueryService.suggest(query).stream()
            .map(portalMeshApiConverter::toMeshSuggestionResponse)
            .toList();
    return PortalSuggestionsResponse.of(items);
  }
}
