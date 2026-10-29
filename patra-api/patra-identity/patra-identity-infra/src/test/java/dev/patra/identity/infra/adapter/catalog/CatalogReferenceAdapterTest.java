package dev.patra.identity.infra.adapter.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.catalog.api.mesh.dto.MeshDescriptorResp;
import dev.patra.catalog.api.mesh.endpoint.MeshEndpoint;
import dev.patra.catalog.api.organization.dto.OrganizationResp;
import dev.patra.catalog.api.organization.endpoint.OrganizationEndpoint;
import dev.patra.identity.domain.exception.CatalogUnavailableException;
import dev.patra.identity.domain.model.vo.profile.MeshUi;
import dev.patra.identity.domain.model.vo.profile.RorId;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/// CatalogReferenceAdapter 单元测试：快照规则与下游错误的翻译。
class CatalogReferenceAdapterTest {

  private static final RorId PUMCH = RorId.of("04jztag35");
  private static final MeshUi DIABETES = MeshUi.of("D003920");

  private final OrganizationEndpoint organizations = mock(OrganizationEndpoint.class);
  private final MeshEndpoint mesh = mock(MeshEndpoint.class);
  private final CatalogReferenceAdapter adapter = new CatalogReferenceAdapter(organizations, mesh);

  @Test
  void should_prefer_stripped_zh_name() {
    when(organizations.getOrganization("04jztag35"))
        .thenReturn(organization(" 北京协和医院 ", "Peking Union Medical College Hospital"));

    assertThat(adapter.organizationName(PUMCH)).contains("北京协和医院");
  }

  @Test
  void should_fall_back_to_display_name_when_zh_blank() {
    when(organizations.getOrganization("04jztag35"))
        .thenReturn(organization("  ", " Peking Union Medical College Hospital "));

    assertThat(adapter.organizationName(PUMCH)).contains("Peking Union Medical College Hospital");
  }

  @Test
  void should_fall_back_to_display_name_when_zh_missing() {
    when(organizations.getOrganization("04jztag35"))
        .thenReturn(organization(null, "Peking Union Medical College Hospital"));

    assertThat(adapter.organizationName(PUMCH)).contains("Peking Union Medical College Hospital");
  }

  @Test
  void should_strip_mesh_name() {
    when(mesh.getDescriptor("D003920"))
        .thenReturn(MeshDescriptorResp.of("D003920", " Diabetes Mellitus ", List.of()));

    assertThat(adapter.meshName(DIABETES)).contains("Diabetes Mellitus");
  }

  @Test
  void should_return_empty_on_remote_not_found() {
    when(organizations.getOrganization("04jztag35"))
        .thenThrow(remote(404, StandardErrorTrait.NOT_FOUND));
    when(mesh.getDescriptor("D003920")).thenThrow(remote(404, StandardErrorTrait.NOT_FOUND));

    assertThat(adapter.organizationName(PUMCH)).isEmpty();
    assertThat(adapter.meshName(DIABETES)).isEmpty();
  }

  @Test
  void should_wrap_other_remote_errors() {
    RemoteCallException failure = remote(500, StandardErrorTrait.DEP_UNAVAILABLE);
    when(mesh.getDescriptor("D003920")).thenThrow(failure);

    assertThatThrownBy(() -> adapter.meshName(DIABETES))
        .isInstanceOf(CatalogUnavailableException.class)
        .hasCause(failure);
  }

  @Test
  void should_wrap_io_failure() {
    ResourceAccessException failure =
        new ResourceAccessException("Connection refused", new IOException("refused"));
    when(organizations.getOrganization("04jztag35")).thenThrow(failure);

    assertThatThrownBy(() -> adapter.organizationName(PUMCH))
        .isInstanceOf(CatalogUnavailableException.class)
        .hasCause(failure);
  }

  @Test
  void should_wrap_missing_instance() {
    IllegalStateException failure =
        new IllegalStateException("No instances available for patra-catalog");
    when(mesh.getDescriptor("D003920")).thenThrow(failure);

    assertThatThrownBy(() -> adapter.meshName(DIABETES))
        .isInstanceOf(CatalogUnavailableException.class)
        .hasCause(failure);
  }

  /// 一个机构响应。
  ///
  /// @param zhName 中文名
  /// @param displayName 显示名
  /// @return 响应
  private static OrganizationResp organization(String zhName, String displayName) {
    return OrganizationResp.builder()
        .rorId("04jztag35")
        .displayName(displayName)
        .zhName(zhName)
        .countryCode("CN")
        .types(List.of("healthcare"))
        .build();
  }

  /// 一个带语义特征的远程调用异常。
  ///
  /// @param status HTTP 状态
  /// @param trait 特征
  /// @return 异常
  private static RemoteCallException remote(int status, StandardErrorTrait trait) {
    return new RemoteCallException(
        "CAT-0" + status, status, "remote", "GET /x", null, Map.of(), Set.of(trait));
  }
}
