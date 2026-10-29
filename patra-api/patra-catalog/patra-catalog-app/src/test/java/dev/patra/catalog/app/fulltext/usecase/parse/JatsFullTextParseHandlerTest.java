package dev.patra.catalog.app.fulltext.usecase.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.port.lock.ExclusiveRunPort;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// `JatsFullTextParseHandler` 单元测试。
@ExtendWith(MockitoExtension.class)
@DisplayName("JatsFullTextParseHandler")
class JatsFullTextParseHandlerTest {

  @Mock private JatsFullTextParseRunner runner;
  @Mock private ExclusiveRunPort exclusiveRunPort;
  @InjectMocks private JatsFullTextParseHandler handler;

  @Test
  @DisplayName("取到锁时运行解析")
  void should_run_when_lock_acquired() {
    when(exclusiveRunPort.runExclusively(eq(ExclusiveRunPort.FULLTEXT_PARSE), any()))
        .thenAnswer(inv -> Optional.ofNullable(((Supplier<?>) inv.getArgument(1)).get()));
    when(runner.run()).thenReturn(FullTextParseRunStats.of(3, 2, 1, 0));

    assertThat(handler.handle(new JatsFullTextParseCommand()))
        .isEqualTo(FullTextParseRunStats.of(3, 2, 1, 0));
  }

  @Test
  @DisplayName("拿不到锁时返回空统计")
  void should_return_empty_stats_when_lock_held() {
    when(exclusiveRunPort.runExclusively(eq(ExclusiveRunPort.FULLTEXT_PARSE), any()))
        .thenReturn(Optional.empty());

    assertThat(handler.handle(new JatsFullTextParseCommand()))
        .isEqualTo(FullTextParseRunStats.empty());
    verifyNoInteractions(runner);
  }
}
