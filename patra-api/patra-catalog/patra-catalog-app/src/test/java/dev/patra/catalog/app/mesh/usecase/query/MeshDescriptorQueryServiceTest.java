package dev.patra.catalog.app.mesh.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.mesh.exception.MeshDescriptorNotFoundException;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSuggestion;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSummary;
import dev.patra.catalog.domain.mesh.port.read.MeshDescriptorReadPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `MeshDescriptorQueryService` 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshDescriptorQueryService 单元测试")
class MeshDescriptorQueryServiceTest {

  private final MeshDescriptorReadPort port = mock(MeshDescriptorReadPort.class);
  private final MeshDescriptorQueryService service = new MeshDescriptorQueryService(port);

  @Test
  @DisplayName("建议直接委托读端口")
  void should_delegate_suggest_to_read_port() {
    SuggestQuery query = SuggestQuery.of("Cancer", 10);
    MeshDescriptorSuggestion suggestion =
        MeshDescriptorSuggestion.of(
            MeshDescriptorSummary.of("D009369", "Neoplasms", List.of("C04")), "Cancer");
    when(port.suggest(query)).thenReturn(List.of(suggestion));

    assertThat(service.suggest(query)).containsExactly(suggestion);
  }

  @Test
  @DisplayName("UI 存在时返回摘要，6 位与 9 位数字都合规")
  void should_return_summary_when_ui_exists() {
    MeshDescriptorSummary short6 =
        MeshDescriptorSummary.of("D003920", "Diabetes Mellitus", List.of());
    MeshDescriptorSummary long9 = MeshDescriptorSummary.of("D000072716", "Cancer Pain", List.of());
    when(port.findByUi("D003920")).thenReturn(Optional.of(short6));
    when(port.findByUi("D000072716")).thenReturn(Optional.of(long9));

    assertThat(service.getByUi("D003920")).isEqualTo(short6);
    assertThat(service.getByUi("D000072716")).isEqualTo(long9);
  }

  @Test
  @DisplayName("UI 不存在时抛 MeshDescriptorNotFoundException")
  void should_throw_not_found_when_ui_absent() {
    when(port.findByUi("D999999")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByUi("D999999"))
        .isInstanceOf(MeshDescriptorNotFoundException.class);
  }

  @Test
  @DisplayName("格式不对的 UI 直接 NotFound，不查端口")
  void should_reject_malformed_ui_without_querying() {
    for (String bad : List.of("D12", "C000657245", "d003920", "D0039201", "")) {
      assertThatThrownBy(() -> service.getByUi(bad))
          .as(bad)
          .isInstanceOf(MeshDescriptorNotFoundException.class);
    }
    verifyNoInteractions(port);
  }
}
