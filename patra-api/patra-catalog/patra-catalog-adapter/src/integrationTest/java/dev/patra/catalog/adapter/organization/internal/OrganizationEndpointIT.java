package dev.patra.catalog.adapter.organization.internal;

import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.app.organization.usecase.query.OrganizationQueryService;
import dev.patra.catalog.domain.organization.exception.OrganizationNotFoundException;
import dev.patra.catalog.domain.organization.model.read.OrganizationSummary;
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

/// `OrganizationEndpointImpl` REST 切片测试：`GET /_internal/organizations/{rorId}` 的 200 与 404。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(controllers = OrganizationEndpointImpl.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import(OrganizationEndpointImpl.class)
@AutoConfigureRestTestClient
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("OrganizationEndpoint REST 切片测试")
class OrganizationEndpointIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private OrganizationQueryService service;

  @Test
  @DisplayName("GET /_internal/organizations/{rorId} 返回机构摘要 JSON")
  void should_return_organization_json() {
    when(service.getByRorId("0aaaaaaa1"))
        .thenReturn(
            OrganizationSummary.builder()
                .rorId("0aaaaaaa1")
                .displayName("Peking Union Medical College Hospital")
                .zhName("北京协和医院")
                .countryCode("CN")
                .cityName("Beijing")
                .types(List.of("funder", "healthcare"))
                .build());

    restClient
        .get()
        .uri("/_internal/organizations/0aaaaaaa1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.rorId")
        .isEqualTo("0aaaaaaa1")
        .jsonPath("$.displayName")
        .isEqualTo("Peking Union Medical College Hospital")
        .jsonPath("$.zhName")
        .isEqualTo("北京协和医院")
        .jsonPath("$.countryCode")
        .isEqualTo("CN")
        .jsonPath("$.types[0]")
        .isEqualTo("funder")
        .jsonPath("$.cityName")
        .doesNotExist();
  }

  @Test
  @DisplayName("不存在时 404 + CATALOG-0404")
  void should_return_404_with_catalog_code_when_not_found() {
    when(service.getByRorId("0zzzzzzz9")).thenThrow(new OrganizationNotFoundException("0zzzzzzz9"));

    restClient
        .get()
        .uri("/_internal/organizations/0zzzzzzz9")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CATALOG-0404");
  }
}
