package dev.patra.interaction.app.discussion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// 楼统计的重算：先拿回复与删除共用的楼锁，再按明细重取。
class ThreadStatsRecountGatewayImplTest {

  private final CommentRepository comments = mock(CommentRepository.class);
  private final ThreadStatsRecountGatewayImpl gateway = new ThreadStatsRecountGatewayImpl(comments);

  @Test
  void should_name_counter_and_find_drift() {
    when(comments.findThreadStatsDrift()).thenReturn(List.of(7L));

    assertThat(gateway.counter()).isEqualTo("discussion.thread_stats");
    assertThat(gateway.findDrifted()).containsExactly(7L);
  }

  @Test
  void should_lock_thread_before_recounting() {
    when(comments.recountThreadStats(7L)).thenReturn(Optional.of(StatsCorrection.of(5, 1)));

    assertThat(gateway.recount(7L)).contains(StatsCorrection.of(5, 1));

    InOrder order = inOrder(comments);
    order.verify(comments).lockThread(7L);
    order.verify(comments).recountThreadStats(7L);
  }
}
