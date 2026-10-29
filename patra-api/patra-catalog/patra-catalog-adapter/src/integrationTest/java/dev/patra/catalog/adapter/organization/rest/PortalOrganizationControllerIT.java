package dev.patra.catalog.adapter.organization.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.adapter.organization.rest.converter.PortalOrganizationApiConverter;
import dev.patra.catalog.app.organization.usecase.query.OrganizationQueryService;
import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.organization.model.read.OrganizationSuggestion;
import dev.patra.catalog.domain.organization.model.read.OrganizationSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// `PortalOrganizationController` REST 切片测试：响应结构、参数归一化与校验。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(controllers = PortalOrganizationController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import({PortalOrganizationController.class, PortalOrganizationApiConverter.class})
@AutoConfigureRestTestClient
@DisplayName("PortalOrganizationController REST 切片测试")
class PortalOrganizationControllerIT {

  private static final String SUGGEST = "/portal/organizations/suggest";

  @Autowired private RestTestClient restClient;

  @MockitoBean private OrganizationQueryService service;

  @Test
  @DisplayName("GET /portal/organizations/suggest 返回 items 信封，limit 缺省 10")
  void should_return_items_with_default_limit_10() {
    when(service.suggest(any()))
        .thenReturn(
            List.of(
                OrganizationSuggestion.of(
                    OrganizationSummary.builder()
                        .rorId("0aaaaaaa1")
                        .displayName("Peking Union Medical College Hospital")
                        .zhName("北京协和医院")
                        .countryCode("CN")
                        .cityName("Beijing")
                        .types(List.of("funder", "healthcare"))
                        .build(),
                    "北京协和医院")));

    restClient
        .get()
        .uri(b -> b.path(SUGGEST).queryParam("q", "协和").build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.items[0].rorId")
        .isEqualTo("0aaaaaaa1")
        .jsonPath("$.items[0].zhName")
        .isEqualTo("北京协和医院")
        .jsonPath("$.items[0].matchedName")
        .isEqualTo("北京协和医院")
        .jsonPath("$.items[0].cityName")
        .isEqualTo("Beijing")
        .jsonPath("$.items[0].types[1]")
        .isEqualTo("healthcare");

    ArgumentCaptor<SuggestQuery> captor = ArgumentCaptor.forClass(SuggestQuery.class);
    verify(service).suggest(captor.capture());
    assertThat(captor.getValue()).isEqualTo(SuggestQuery.of("协和", 10));
  }

  @Test
  @DisplayName("q 归一化后传给服务，limit 原样传")
  void should_pass_normalized_query_and_limit() {
    when(service.suggest(any())).thenReturn(List.of());

    restClient
        .get()
        .uri(b -> b.path(SUGGEST).queryParam("q", "  Mayo  Clinic ").queryParam("limit", 5).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items")
        .isArray()
        .jsonPath("$.items.length()")
        .isEqualTo(0);

    verify(service).suggest(SuggestQuery.of("Mayo Clinic", 5));
  }

  @Test
  @DisplayName("q 缺失 / 1 字 / 101 字、limit 0 / 21 都是 422，不触达服务")
  void should_reject_invalid_parameters_with_422() {
    for (String uri :
        List.of(
            SUGGEST,
            SUGGEST + "?q=a",
            SUGGEST + "?q=" + "a".repeat(101),
            SUGGEST + "?q=Mayo&limit=0",
            SUGGEST + "?q=Mayo&limit=21")) {
      restClient.get().uri(uri).exchange().expectStatus().isEqualTo(422);
    }

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("limit 不是数字时 4xx，不触达服务")
  void should_reject_non_numeric_limit_without_touching_service() {
    restClient
        .get()
        .uri(SUGGEST + "?q=Mayo&limit=abc")
        .exchange()
        .expectStatus()
        .is4xxClientError();

    verifyNoInteractions(service);
  }
}
