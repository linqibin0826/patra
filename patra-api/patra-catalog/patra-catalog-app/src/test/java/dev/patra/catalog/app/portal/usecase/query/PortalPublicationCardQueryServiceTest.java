package dev.patra.catalog.app.portal.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.portal.model.read.PortalPaperReadModel;
import dev.patra.catalog.domain.portal.port.read.PublicationSearchReadPort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PortalPublicationCardQueryService` 单元测试：去重、按请求顺序重排、跳过不存在与软删。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PortalPublicationCardQueryService 单元测试")
class PortalPublicationCardQueryServiceTest {

  private final PublicationSearchReadPort port = mock(PublicationSearchReadPort.class);
  private final PortalPublicationCardQueryService service =
      new PortalPublicationCardQueryService(port);

  @Test
  @DisplayName("按请求顺序重排，端口没返回的 ID 跳过")
  void should_reorder_to_request_order_and_skip_missing() {
    when(port.findCardsByIds(List.of(3L, 1L, 2L))).thenReturn(List.of(card(1L), card(3L)));

    assertThat(service.findByIds(List.of(3L, 1L, 2L)))
        .extracting(PortalPaperReadModel::id)
        .containsExactly(3L, 1L);
  }

  @Test
  @DisplayName("查询前去重，保留第一次出现的位置")
  void should_deduplicate_before_querying() {
    when(port.findCardsByIds(List.of(4L, 6L))).thenReturn(List.of(card(6L), card(4L)));

    assertThat(service.findByIds(List.of(4L, 4L, 6L)))
        .extracting(PortalPaperReadModel::id)
        .containsExactly(4L, 6L);
    verify(port).findCardsByIds(List.of(4L, 6L));
  }

  private static PortalPaperReadModel card(Long id) {
    return PortalPaperReadModel.builder().id(id).title("t" + id).build();
  }
}
