package dev.patra.catalog.app.fulltext.usecase.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.port.lock.ExclusiveRunPort;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// `PmcFullTextFetchHandler` 单元测试。
@ExtendWith(MockitoExtension.class)
@DisplayName("PmcFullTextFetchHandler")
class PmcFullTextFetchHandlerTest {

  @Mock private PmcFullTextFetchRunner runner;
  @Mock private ExclusiveRunPort exclusiveRunPort;
  @InjectMocks private PmcFullTextFetchHandler handler;

  @Test
  @DisplayName("取到锁时把文献 ID 透传给 Runner")
  void should_run_with_given_ids_when_lock_acquired() {
    when(exclusiveRunPort.runExclusively(eq(ExclusiveRunPort.FULLTEXT_FETCH), any()))
        .thenAnswer(inv -> Optional.ofNullable(((Supplier<?>) inv.getArgument(1)).get()));
    when(runner.run(List.of(3L, 4L))).thenReturn(FullTextFetchRunStats.of(2, 1, 1, 0));

    assertThat(handler.handle(new PmcFullTextFetchCommand(List.of(3L, 4L))))
        .isEqualTo(FullTextFetchRunStats.of(2, 1, 1, 0));
  }

  @Test
  @DisplayName("拿不到锁时返回空统计、不运行")
  void should_return_empty_stats_when_lock_held() {
    when(exclusiveRunPort.runExclusively(eq(ExclusiveRunPort.FULLTEXT_FETCH), any()))
        .thenReturn(Optional.empty());

    assertThat(handler.handle(new PmcFullTextFetchCommand(null)))
        .isEqualTo(FullTextFetchRunStats.empty());
    verifyNoInteractions(runner);
  }

  @Test
  @DisplayName("命令把 null 视为空列表")
  void should_treat_null_ids_as_empty() {
    assertThat(new PmcFullTextFetchCommand(null).publicationIds()).isEmpty();
  }
}
