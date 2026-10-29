package dev.patra.catalog.adapter.mesh.internal;

import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.app.mesh.usecase.query.MeshDescriptorQueryService;
import dev.patra.catalog.domain.mesh.exception.MeshDescriptorNotFoundException;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// `MeshEndpointImpl` REST 切片测试：`GET /_internal/mesh/descriptors/{ui}` 的 200 与 404。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(controllers = MeshEndpointImpl.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import(MeshEndpointImpl.class)
@AutoConfigureRestTestClient
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("MeshEndpoint REST 切片测试")
class MeshEndpointIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private MeshDescriptorQueryService service;

  @Test
  @DisplayName("GET /_internal/mesh/descriptors/{ui} 返回主题词摘要 JSON")
  void should_return_descriptor_json() {
    when(service.getByUi("D003920"))
        .thenReturn(
            MeshDescriptorSummary.of(
                "D003920", "Diabetes Mellitus", List.of("C18.452.394.750", "C19.246")));

    restClient
        .get()
        .uri("/_internal/mesh/descriptors/D003920")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.ui")
        .isEqualTo("D003920")
        .jsonPath("$.name")
        .isEqualTo("Diabetes Mellitus")
        .jsonPath("$.treeNumbers[0]")
        .isEqualTo("C18.452.394.750")
        .jsonPath("$.treeNumbers[1]")
        .isEqualTo("C19.246");
  }

  @Test
  @DisplayName("不存在时 404 + CATALOG-0404")
  void should_return_404_with_catalog_code_when_not_found() {
    when(service.getByUi("D999999")).thenThrow(new MeshDescriptorNotFoundException("D999999"));

    restClient
        .get()
        .uri("/_internal/mesh/descriptors/D999999")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CATALOG-0404");
  }
}
