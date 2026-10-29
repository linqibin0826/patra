package dev.patra.catalog.adapter.rest.portal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.adapter.rest.portal.response.PortalMeshSuggestionResponse;
import dev.patra.catalog.domain.model.read.mesh.MeshDescriptorSuggestion;
import dev.patra.catalog.domain.model.read.mesh.MeshDescriptorSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PortalMeshApiConverter` 转换测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PortalMeshApiConverter MeSH 建议转换")
class PortalMeshApiConverterTest {

  private final PortalMeshApiConverter converter = new PortalMeshApiConverter();

  @Test
  @DisplayName("MeSH 候选逐字段映射")
  void should_map_mesh_suggestion() {
    MeshDescriptorSuggestion suggestion =
        MeshDescriptorSuggestion.of(
            MeshDescriptorSummary.of("D009369", "Neoplasms", List.of("C04")), "Cancer");

    assertThat(converter.toMeshSuggestionResponse(suggestion))
        .isEqualTo(
            PortalMeshSuggestionResponse.of("D009369", "Neoplasms", "Cancer", List.of("C04")));
  }
}
