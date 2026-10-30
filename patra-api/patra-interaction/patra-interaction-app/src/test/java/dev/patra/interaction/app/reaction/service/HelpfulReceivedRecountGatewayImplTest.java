package dev.patra.interaction.app.reaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 获赞的重算：两步都交给仓储（仓储锁用户统计行）。
class HelpfulReceivedRecountGatewayImplTest {

  private final HelpfulRepository helpful = mock(HelpfulRepository.class);
  private final HelpfulReceivedRecountGatewayImpl gateway =
      new HelpfulReceivedRecountGatewayImpl(helpful);

  @Test
  void should_name_counter_and_delegate() {
    when(helpful.findReceivedDrift()).thenReturn(List.of(9L));
    when(helpful.recountReceived(9L)).thenReturn(Optional.of(StatsCorrection.of(7, 2)));

    assertThat(gateway.counter()).isEqualTo("reaction.helpful_received");
    assertThat(gateway.findDrifted()).containsExactly(9L);
    assertThat(gateway.recount(9L)).contains(StatsCorrection.of(7, 2));
  }
}
