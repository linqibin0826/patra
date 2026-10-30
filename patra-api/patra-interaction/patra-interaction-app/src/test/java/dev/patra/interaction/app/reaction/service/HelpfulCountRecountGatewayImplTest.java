package dev.patra.interaction.app.reaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// 有帮助数的重算：先拿与点、取消、删除联动相同的目标锁，再按明细重数。
class HelpfulCountRecountGatewayImplTest {

  private static final HelpfulTarget C1 = HelpfulTarget.of(HelpfulTargetType.COMMENT, 101L);

  private final HelpfulRepository helpful = mock(HelpfulRepository.class);
  private final HelpfulCountRecountGatewayImpl gateway =
      new HelpfulCountRecountGatewayImpl(helpful);

  @Test
  void should_name_counter_and_find_drift() {
    when(helpful.findHelpfulCountDrift()).thenReturn(List.of(C1));

    assertThat(gateway.counter()).isEqualTo("reaction.helpful_count");
    assertThat(gateway.findDrifted()).containsExactly(C1);
  }

  @Test
  void should_lock_target_before_recounting() {
    when(helpful.recountHelpfulCount(C1)).thenReturn(Optional.of(StatsCorrection.of(5, 2)));

    assertThat(gateway.recount(C1)).contains(StatsCorrection.of(5, 2));

    InOrder order = inOrder(helpful);
    order.verify(helpful).lockTarget(C1);
    order.verify(helpful).recountHelpfulCount(C1);
  }
}
