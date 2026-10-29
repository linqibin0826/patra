package dev.patra.catalog.app.organization.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.organization.exception.OrganizationNotFoundException;
import dev.patra.catalog.domain.organization.model.read.OrganizationSuggestion;
import dev.patra.catalog.domain.organization.model.read.OrganizationSummary;
import dev.patra.catalog.domain.organization.port.read.OrganizationReadPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `OrganizationQueryService` 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationQueryService 单元测试")
class OrganizationQueryServiceTest {

  private final OrganizationReadPort port = mock(OrganizationReadPort.class);
  private final OrganizationQueryService service = new OrganizationQueryService(port);

  @Test
  @DisplayName("建议直接委托读端口")
  void should_delegate_suggest_to_read_port() {
    SuggestQuery query = SuggestQuery.of("协和", 10);
    OrganizationSuggestion suggestion =
        OrganizationSuggestion.of(
            OrganizationSummary.builder().rorId("0aaaaaaa1").displayName("X").build(), "协和医院");
    when(port.suggest(query)).thenReturn(List.of(suggestion));

    assertThat(service.suggest(query)).containsExactly(suggestion);
  }

  @Test
  @DisplayName("ROR ID 存在时返回摘要")
  void should_return_summary_when_ror_id_exists() {
    OrganizationSummary summary =
        OrganizationSummary.builder().rorId("0aaaaaaa1").displayName("X").build();
    when(port.findByRorId("0aaaaaaa1")).thenReturn(Optional.of(summary));

    assertThat(service.getByRorId("0aaaaaaa1")).isEqualTo(summary);
  }

  @Test
  @DisplayName("ROR ID 不存在时抛 OrganizationNotFoundException")
  void should_throw_not_found_when_ror_id_absent() {
    when(port.findByRorId("0aaaaaaa1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByRorId("0aaaaaaa1"))
        .isInstanceOf(OrganizationNotFoundException.class);
  }

  @Test
  @DisplayName("格式不对的 ROR ID 直接 NotFound，不查端口")
  void should_reject_malformed_ror_id_without_querying() {
    for (String bad : List.of("04JZTAG35", "https://ror.org/04jztag35", "abc", "")) {
      assertThatThrownBy(() -> service.getByRorId(bad))
          .as(bad)
          .isInstanceOf(OrganizationNotFoundException.class);
    }
    verifyNoInteractions(port);
  }
}
