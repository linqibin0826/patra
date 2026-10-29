package dev.patra.catalog.app.portal.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.portal.exception.PublicationNotFoundException;
import dev.patra.catalog.domain.portal.model.read.PublicationDetailReadModel;
import dev.patra.catalog.domain.portal.model.vo.EvidenceLevel;
import dev.patra.catalog.domain.portal.port.read.PublicationDetailReadPort;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortalPublicationDetailQueryService 单元测试")
class PortalPublicationDetailQueryServiceTest {

  @Mock private PublicationDetailReadPort readPort;
  @InjectMocks private PortalPublicationDetailQueryService service;

  @Test
  @DisplayName("readPort 返回 present 时，getById 返回读模型")
  void shouldReturnModelWhenFound() {
    PublicationDetailReadModel model = stubModel(123L);
    when(readPort.findById(123L)).thenReturn(Optional.of(model));

    PublicationDetailReadModel result = service.getById(123L);

    assertThat(result).isSameAs(model);
  }

  @Test
  @DisplayName("readPort 返回 empty 时，getById 抛出 PublicationNotFoundException")
  void shouldThrowWhenNotFound() {
    when(readPort.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(999L))
        .isInstanceOf(PublicationNotFoundException.class)
        .hasMessageContaining("999");
  }

  private PublicationDetailReadModel stubModel(long id) {
    return PublicationDetailReadModel.builder()
        .id(id)
        .title("Test Publication")
        .publicationYear(2024)
        .evidenceLevel(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL)
        .isOa(false)
        .build();
  }
}
