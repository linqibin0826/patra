package dev.patra.interaction.app.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 收藏人数的重算：计数名固定，两步都交给文献库仓储。
class CollectorCountRecountGatewayImplTest {

  private final LibraryRepository library = mock(LibraryRepository.class);
  private final CollectorCountRecountGatewayImpl gateway =
      new CollectorCountRecountGatewayImpl(library);

  @Test
  void should_name_counter() {
    assertThat(gateway.counter()).isEqualTo("collection.collector_count");
  }

  @Test
  void should_delegate_both_steps_to_library() {
    when(library.findCollectorCountDrift()).thenReturn(List.of(SubjectId.of(11L)));
    when(library.recountCollectorCount(SubjectId.of(11L)))
        .thenReturn(Optional.of(StatsCorrection.of(5, 2)));

    assertThat(gateway.findDrifted()).containsExactly(SubjectId.of(11L));
    assertThat(gateway.recount(SubjectId.of(11L))).contains(StatsCorrection.of(5, 2));
  }
}
