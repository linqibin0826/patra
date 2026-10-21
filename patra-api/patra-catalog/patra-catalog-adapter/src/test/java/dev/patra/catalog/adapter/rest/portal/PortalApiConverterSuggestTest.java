package dev.patra.catalog.adapter.rest.portal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.adapter.rest.portal.response.PortalMeshSuggestionResponse;
import dev.patra.catalog.adapter.rest.portal.response.PortalOrganizationSuggestionResponse;
import dev.patra.catalog.domain.model.read.mesh.MeshDescriptorSuggestion;
import dev.patra.catalog.domain.model.read.mesh.MeshDescriptorSummary;
import dev.patra.catalog.domain.model.read.organization.OrganizationSuggestion;
import dev.patra.catalog.domain.model.read.organization.OrganizationSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PortalApiConverter` 对机构与 MeSH 建议读模型的转换测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PortalApiConverter 建议读模型转换")
class PortalApiConverterSuggestTest {

  private final PortalApiConverter converter = new PortalApiConverter();

  @Test
  @DisplayName("机构候选逐字段映射")
  void should_map_organization_suggestion() {
    OrganizationSuggestion suggestion =
        OrganizationSuggestion.of(
            OrganizationSummary.builder()
                .rorId("0aaaaaaa1")
                .displayName("Peking Union Medical College Hospital")
                .zhName("北京协和医院")
                .countryCode("CN")
                .cityName("Beijing")
                .types(List.of("funder", "healthcare"))
                .build(),
            "PUMCH");

    PortalOrganizationSuggestionResponse response =
        converter.toOrganizationSuggestionResponse(suggestion);

    assertThat(response.rorId()).isEqualTo("0aaaaaaa1");
    assertThat(response.displayName()).isEqualTo("Peking Union Medical College Hospital");
    assertThat(response.zhName()).isEqualTo("北京协和医院");
    assertThat(response.matchedName()).isEqualTo("PUMCH");
    assertThat(response.countryCode()).isEqualTo("CN");
    assertThat(response.cityName()).isEqualTo("Beijing");
    assertThat(response.types()).containsExactly("funder", "healthcare");
  }

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
