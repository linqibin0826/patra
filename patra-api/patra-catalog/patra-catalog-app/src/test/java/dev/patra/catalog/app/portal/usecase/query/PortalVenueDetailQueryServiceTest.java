package dev.patra.catalog.app.portal.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.portal.exception.VenueNotFoundException;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel;
import dev.patra.catalog.domain.portal.port.read.VenueDetailReadPort;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortalVenueDetailQueryService 单元测试")
class PortalVenueDetailQueryServiceTest {

  @Mock private VenueDetailReadPort readPort;
  @InjectMocks private PortalVenueDetailQueryService service;

  @Test
  @DisplayName("readPort 返回 present 时，getById 返回读模型")
  void shouldReturnModelWhenFound() {
    VenueDetailReadModel model = stubModel(123L);
    when(readPort.findById(123L)).thenReturn(Optional.of(model));

    VenueDetailReadModel result = service.getById(123L);

    assertThat(result).isSameAs(model);
  }

  @Test
  @DisplayName("readPort 返回 empty 时，getById 抛出 VenueNotFoundException")
  void shouldThrowWhenNotFound() {
    when(readPort.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(999L))
        .isInstanceOf(VenueNotFoundException.class)
        .hasMessageContaining("999");
  }

  private VenueDetailReadModel stubModel(long id) {
    return VenueDetailReadModel.builder()
        .id(id)
        .title("Nature")
        .abbreviatedTitle("Nat")
        .venueType("JOURNAL")
        .foundedYear(1869)
        .isOpenAccess(true)
        .build();
  }
}
