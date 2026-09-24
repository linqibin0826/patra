package dev.linqibin.patra.catalog.app.usecase.portal.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.linqibin.patra.catalog.domain.port.read.PublicationSearchReadPort;
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
