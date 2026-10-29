package dev.patra.catalog.app.portal.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.PagingParams;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFacets;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFilter;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseReadModel;
import dev.patra.catalog.domain.portal.port.read.VenueBrowseReadPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortalVenueBrowseQueryService 单元测试")
class PortalVenueBrowseQueryServiceTest {

  @Mock private VenueBrowseReadPort readPort;
  @InjectMocks private PortalVenueBrowseQueryService service;

  @Test
  @DisplayName("browse 直接委托 readPort.search，透传 filter 和 paging")
  void shouldDelegateToReadPort() {
    PagingParams paging = PagingParams.normalize(1, 12, 12, 50);
    VenueBrowseFilter filter = VenueBrowseFilter.builder().build();
    PageResult<VenueBrowseReadModel> expected = PageResult.empty(1, 12);
    when(readPort.search(filter, paging)).thenReturn(expected);

    PageResult<VenueBrowseReadModel> result = service.browse(filter, paging);

    assertThat(result).isSameAs(expected);
    verify(readPort).search(filter, paging);
  }

  @Test
  @DisplayName("facets 直接委托 readPort.facets，透传 filter")
  void shouldDelegateFacetsToReadPort() {
    VenueBrowseFilter filter = VenueBrowseFilter.builder().build();
    VenueBrowseFacets expected =
        VenueBrowseFacets.builder().casTop(10).openAccess(5).doaj(3).build();
    when(readPort.facets(filter)).thenReturn(expected);

    VenueBrowseFacets result = service.facets(filter);

    assertThat(result).isSameAs(expected);
    verify(readPort).facets(filter);
  }
}
