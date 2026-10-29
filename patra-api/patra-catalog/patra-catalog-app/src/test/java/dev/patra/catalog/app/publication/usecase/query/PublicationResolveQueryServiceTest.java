package dev.patra.catalog.app.publication.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.publication.model.read.PublicationResolveReadModel;
import dev.patra.catalog.domain.publication.port.read.PublicationResolveReadPort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PublicationResolveQueryService` 单元测试：去重、按请求顺序、为查不到的键补行。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationResolveQueryService 单元测试")
class PublicationResolveQueryServiceTest {

  private final PublicationResolveReadPort port = mock(PublicationResolveReadPort.class);
  private final PublicationResolveQueryService service = new PublicationResolveQueryService(port);

  @Test
  @DisplayName("按 ID：保持请求顺序，查不到的键补 found = null")
  void should_keep_request_order_and_fill_missing_by_ids() {
    PublicationResolveReadModel m1 = model(1L, "101");
    PublicationResolveReadModel m3 = model(3L, "103");
    when(port.findByIds(List.of(3L, 1L, 2L))).thenReturn(List.of(m1, m3));

    List<PublicationResolution> result = service.resolveByIds(List.of(3L, 1L, 2L));

    assertThat(result).extracting(PublicationResolution::key).containsExactly("3", "1", "2");
    assertThat(result).extracting(PublicationResolution::found).containsExactly(m3, m1, null);
  }

  @Test
  @DisplayName("重复的键只输出第一次，端口只被调一次且入参已去重")
  void should_deduplicate_and_query_port_once() {
    when(port.findByIds(List.of(5L, 7L))).thenReturn(List.of(model(5L, "105")));

    List<PublicationResolution> result = service.resolveByIds(List.of(5L, 5L, 7L));

    assertThat(result).extracting(PublicationResolution::key).containsExactly("5", "7");
    verify(port, times(1)).findByIds(List.of(5L, 7L));
  }

  @Test
  @DisplayName("按 PMID：按 PMID 匹配，查不到补 null")
  void should_match_by_pmid() {
    PublicationResolveReadModel m = model(9L, "1");
    when(port.findByPmids(List.of("2", "1"))).thenReturn(List.of(m));

    List<PublicationResolution> result = service.resolveByPmids(List.of("2", "1"));

    assertThat(result).extracting(PublicationResolution::key).containsExactly("2", "1");
    assertThat(result).extracting(PublicationResolution::found).containsExactly(null, m);
  }

  private static PublicationResolveReadModel model(Long id, String pmid) {
    return PublicationResolveReadModel.builder()
        .id(id)
        .pmid(pmid)
        .title("t" + id)
        .publicationYear(2021)
        .deleted(false)
        .build();
  }
}
