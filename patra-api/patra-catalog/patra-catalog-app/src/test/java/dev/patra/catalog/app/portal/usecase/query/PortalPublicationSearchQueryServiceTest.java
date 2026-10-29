package dev.patra.catalog.app.portal.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.PagingParams;
import dev.patra.catalog.domain.portal.model.read.PortalPaperReadModel;
import dev.patra.catalog.domain.portal.model.read.PublicationSearchFacets;
import dev.patra.catalog.domain.portal.model.read.PublicationSearchFilter;
import dev.patra.catalog.domain.portal.port.read.PublicationSearchReadPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortalPublicationSearchQueryService 单元测试")
class PortalPublicationSearchQueryServiceTest {

  @Mock private PublicationSearchReadPort readPort;
  @InjectMocks private PortalPublicationSearchQueryService service;

  @Test
  @DisplayName("search 直接委托 readPort.search，透传 filter 与 paging")
  void searchDelegates() {
    PagingParams paging = PagingParams.of(1, 20);
    PublicationSearchFilter filter = PublicationSearchFilter.builder().keyword("glp").build();
    PageResult<PortalPaperReadModel> expected = PageResult.empty(1, 20);
    when(readPort.search(filter, paging)).thenReturn(expected);

    assertThat(service.search(filter, paging)).isSameAs(expected);
    verify(readPort).search(filter, paging);
  }

  @Test
  @DisplayName("facets 直接委托 readPort.facets")
  void facetsDelegates() {
    PublicationSearchFilter filter = PublicationSearchFilter.builder().build();
    PublicationSearchFacets expected = PublicationSearchFacets.builder().total(3).build();
    when(readPort.facets(filter)).thenReturn(expected);

    assertThat(service.facets(filter)).isSameAs(expected);
    verify(readPort).facets(filter);
  }
}
