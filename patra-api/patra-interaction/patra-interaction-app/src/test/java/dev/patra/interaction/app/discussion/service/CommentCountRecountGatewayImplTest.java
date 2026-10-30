package dev.patra.interaction.app.discussion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 对象评论总数的重算：两步都交给评论仓储（仓储锁对象统计行）。
class CommentCountRecountGatewayImplTest {

  private final CommentRepository comments = mock(CommentRepository.class);
  private final CommentCountRecountGatewayImpl gateway =
      new CommentCountRecountGatewayImpl(comments);

  @Test
  void should_name_counter_and_delegate() {
    when(comments.findCommentCountDrift()).thenReturn(List.of(SubjectId.of(11L)));
    when(comments.recountCommentCount(SubjectId.of(11L)))
        .thenReturn(Optional.of(StatsCorrection.of(7, 2)));

    assertThat(gateway.counter()).isEqualTo("discussion.comment_count");
    assertThat(gateway.findDrifted()).containsExactly(SubjectId.of(11L));
    assertThat(gateway.recount(SubjectId.of(11L))).contains(StatsCorrection.of(7, 2));
  }
}
