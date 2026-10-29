package dev.patra.catalog.adapter.portal.rest;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.adapter.portal.rest.converter.PortalApiConverter;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueBrowseQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueCardQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueDetailQueryService;
import dev.patra.catalog.domain.portal.exception.VenueNotFoundException;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@WebMvcTest(controllers = PortalVenueController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import({PortalVenueController.class, PortalApiConverter.class})
@AutoConfigureRestTestClient
@org.springframework.test.context.TestPropertySource(
    properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("PortalVenueController 期刊详情 REST 切片测试")
class PortalVenueDetailControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private PortalVenueDetailQueryService portalVenueDetailQueryService;

  // PortalVenueBrowseQueryService 也需要 mock（Controller 里已注入）
  @MockitoBean private PortalVenueBrowseQueryService portalVenueBrowseQueryService;

  @MockitoBean private PortalVenueCardQueryService portalVenueCardQueryService;

  @Test
  @DisplayName("GET /portal/venues/{id} 返回 200 + 对齐前端的期刊详情")
  void shouldReturnVenueDetail() {
    VenueDetailReadModel model =
        VenueDetailReadModel.builder()
            .id(319041872872550658L)
            .title("Annals of Oncology")
            .abbreviatedTitle("Ann Oncol")
            .venueType("JOURNAL")
            .issnL("1569-8041")
            .countryCode("NL")
            .primaryLanguage("ENG")
            .foundedYear(1990)
            .isOpenAccess(true)
            .impactFactor(new BigDecimal("51.1"))
            .jcrQuartile("Q1")
            .jcrSubject("ONCOLOGY")
            .build();
    when(portalVenueDetailQueryService.getById(319041872872550658L)).thenReturn(model);

    restClient
        .get()
        .uri("/portal/venues/319041872872550658")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo("319041872872550658")
        .jsonPath("$.title")
        .isEqualTo("Annals of Oncology")
        .jsonPath("$.homepageUrl")
        .isEmpty();
  }

  @Test
  @DisplayName("id=0 时路径参数校验失败返回 422，不触达应用层")
  void shouldRejectNonPositiveVenueId() {
    restClient.get().uri("/portal/venues/0").exchange().expectStatus().isEqualTo(422);

    verifyNoInteractions(portalVenueDetailQueryService);
  }

  @Test
  @DisplayName("期刊不存在时返回 404 + CATALOG-0404 错误码")
  void shouldReturn404WhenVenueNotFound() {
    when(portalVenueDetailQueryService.getById(999L)).thenThrow(new VenueNotFoundException(999L));

    restClient
        .get()
        .uri("/portal/venues/999")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CATALOG-0404");
  }
}
