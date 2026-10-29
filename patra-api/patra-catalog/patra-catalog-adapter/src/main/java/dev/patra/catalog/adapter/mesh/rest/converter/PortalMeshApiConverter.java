package dev.patra.catalog.adapter.mesh.rest.converter;

import dev.patra.catalog.adapter.mesh.rest.response.PortalMeshSuggestionResponse;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSuggestion;
import org.springframework.stereotype.Component;

/// 门户 MeSH 建议读模型 → 响应 DTO 转换器。
///
/// @author linqibin
/// @since 0.1.0
@Component
public class PortalMeshApiConverter {

  /// 将 MeSH 建议候选转为响应 DTO。
  ///
  /// @param suggestion MeSH 建议候选
  /// @return 响应 DTO
  public PortalMeshSuggestionResponse toMeshSuggestionResponse(
      MeshDescriptorSuggestion suggestion) {
    return PortalMeshSuggestionResponse.of(
        suggestion.descriptor().ui(),
        suggestion.descriptor().name(),
        suggestion.matchedTerm(),
        suggestion.descriptor().treeNumbers());
  }
}
