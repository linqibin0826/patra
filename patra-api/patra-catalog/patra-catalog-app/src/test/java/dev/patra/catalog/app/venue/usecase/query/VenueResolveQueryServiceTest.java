package dev.patra.catalog.app.venue.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.venue.model.read.VenueResolveReadModel;
import dev.patra.catalog.domain.venue.port.read.VenueResolveReadPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `VenueResolveQueryService` 单元测试：去重、按请求顺序、为查不到的键补行、副 NLM 对回主 NLM 的期刊。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueResolveQueryService 单元测试")
class VenueResolveQueryServiceTest {

  private final VenueResolveReadPort port = mock(VenueResolveReadPort.class);
  private final VenueResolveQueryService service = new VenueResolveQueryService(port);

  @Test
  @DisplayName("按 ID：每个去重后的键一行，按请求顺序，查不到的补 found = null，端口只调一次")
  void should_output_one_row_per_distinct_id_in_request_order() {
    VenueResolveReadModel m1 = model(1L, "0000001");
    VenueResolveReadModel m3 = model(3L, "0000003");
    when(port.findByIds(List.of(3L, 1L, 2L))).thenReturn(List.of(m1, m3));

    List<VenueResolution> rows = service.resolveByIds(List.of(3L, 1L, 3L, 2L));

    assertThat(rows).extracting(VenueResolution::key).containsExactly("3", "1", "2");
    assertThat(rows).extracting(VenueResolution::found).containsExactly(m3, m1, null);
    verify(port, times(1)).findByIds(List.of(3L, 1L, 2L));
  }

  @Test
  @DisplayName("按 NLM：主、副 NLM 各一行，都对到主 NLM 的期刊；重复键只留第一次，查不到补 null")
  void should_map_secondary_nlm_key_to_venue_with_primary_nlm() {
    VenueResolveReadModel swiss = model(9L, "0404401");
    when(port.findByNlmIds(List.of("0404401", "100970884", "9999999")))
        .thenReturn(Map.of("0404401", swiss, "100970884", swiss));

    List<VenueResolution> rows =
        service.resolveByNlmIds(List.of("0404401", "100970884", "0404401", "9999999"));

    assertThat(rows)
        .extracting(VenueResolution::key)
        .containsExactly("0404401", "100970884", "9999999");
    assertThat(rows).extracting(VenueResolution::found).containsExactly(swiss, swiss, null);
    assertThat(rows.get(1).found().nlmId()).isEqualTo("0404401");
    verify(port, times(1)).findByNlmIds(List.of("0404401", "100970884", "9999999"));
  }

  /// 读模型夹具。
  ///
  /// @param id 期刊 ID
  /// @param nlmId 主 NLM
  /// @return 读模型
  private static VenueResolveReadModel model(long id, String nlmId) {
    return VenueResolveReadModel.builder()
        .id(id)
        .nlmId(nlmId)
        .title("Journal " + id)
        .abbreviatedTitle("J " + id)
        .deleted(false)
        .build();
  }
}
