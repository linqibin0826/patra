package dev.patra.catalog.app.portal.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.portal.model.read.VenueBrowseReadModel;
import dev.patra.catalog.domain.portal.port.read.VenueBrowseReadPort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PortalVenueCardQueryService` 单元测试：去重、按请求顺序重排、跳过不存在与软删的期刊。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PortalVenueCardQueryService 单元测试")
class PortalVenueCardQueryServiceTest {

  private final VenueBrowseReadPort port = mock(VenueBrowseReadPort.class);
  private final PortalVenueCardQueryService service = new PortalVenueCardQueryService(port);

  @Test
  @DisplayName("按请求顺序重排、去重后只查一次端口，端口没返回的 ID 跳过")
  void should_reorder_by_request_dedupe_and_drop_missing() {
    VenueBrowseReadModel one = card(1L);
    VenueBrowseReadModel two = card(2L);
    when(port.findCardsByIds(List.of(2L, 1L, 3L))).thenReturn(List.of(one, two));

    List<VenueBrowseReadModel> cards = service.findByIds(List.of(2L, 1L, 2L, 3L));

    assertThat(cards).containsExactly(two, one);
    verify(port, times(1)).findCardsByIds(List.of(2L, 1L, 3L));
  }

  /// 卡片夹具。
  ///
  /// @param id 期刊 ID
  /// @return 卡片读模型
  private static VenueBrowseReadModel card(long id) {
    return VenueBrowseReadModel.builder().id(id).name("Journal " + id).nlmId("000000" + id).build();
  }
}
