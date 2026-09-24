package dev.linqibin.patra.catalog.app.usecase.portal.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.app.usecase.portal.query.dto.PortalFeedQuery;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchSort;
import dev.linqibin.patra.catalog.domain.port.read.PublicationSearchReadPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortalFeedQueryService 单元测试（feed = 无筛选的检索）")
class PortalFeedQueryServiceTest {

  @Mock private PublicationSearchReadPort readPort;
  @InjectMocks private PortalFeedQueryService service;

  @Test
  @DisplayName("默认 pageSize=14，tab 缺省映射为 LATEST，filter 无任何筛选")
  void shouldApplyDefaults() {
    when(readPort.search(any(), any())).thenReturn(PageResult.empty(1, 14));

    service.listFeed(PortalFeedQuery.of(null, null, null));

    ArgumentCaptor<PublicationSearchFilter> filter =
        ArgumentCaptor.forClass(PublicationSearchFilter.class);
    ArgumentCaptor<PagingParams> paging = ArgumentCaptor.forClass(PagingParams.class);
    verify(readPort).search(filter.capture(), paging.capture());
    assertThat(paging.getValue().page()).isEqualTo(1);
    assertThat(paging.getValue().pageSize()).isEqualTo(14);
    assertThat(filter.getValue().sort()).isEqualTo(PublicationSearchSort.LATEST);
    assertThat(filter.getValue()).isEqualTo(PublicationSearchFilter.builder().build());
  }

  @Test
  @DisplayName("tab=cited（大小写不敏感）映射为 CITED；pageSize 超过 50 截断为 50")
  void shouldMapCitedAndCapPageSize() {
    when(readPort.search(any(), any())).thenReturn(PageResult.empty(1, 50));

    service.listFeed(PortalFeedQuery.of("Cited", 1, 999));

    ArgumentCaptor<PublicationSearchFilter> filter =
        ArgumentCaptor.forClass(PublicationSearchFilter.class);
    ArgumentCaptor<PagingParams> paging = ArgumentCaptor.forClass(PagingParams.class);
    verify(readPort).search(filter.capture(), paging.capture());
    assertThat(filter.getValue().sort()).isEqualTo(PublicationSearchSort.CITED);
    assertThat(paging.getValue().pageSize()).isEqualTo(50);
  }

  @Test
  @DisplayName("非法 tab 抛 IllegalArgumentException")
  void shouldRejectInvalidTab() {
    assertThatThrownBy(() -> service.listFeed(PortalFeedQuery.of("hottest", 1, 14)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
