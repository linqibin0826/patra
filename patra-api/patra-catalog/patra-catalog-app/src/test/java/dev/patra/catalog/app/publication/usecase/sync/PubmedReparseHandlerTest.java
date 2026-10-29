package dev.patra.catalog.app.publication.usecase.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.port.lock.ExclusiveRunPort;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.port.batch.PublicationBatchPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PubmedReparseHandler` 单元测试：与文件导入共用一把锁，每次一个新的作业 generation。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedReparseHandler 单元测试")
class PubmedReparseHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-24T05:00:00Z");

  private final PublicationBatchPort batchPort = mock(PublicationBatchPort.class);
  private final ExclusiveRunPort lock = mock(ExclusiveRunPort.class);
  private final PubmedReparseHandler handler =
      new PubmedReparseHandler(batchPort, lock, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("拿到锁：以 reparse-<毫秒> 为 generation 启动重算，返回计数")
  void should_launch_reparse_with_new_generation() {
    when(lock.runExclusively(eq(ExclusiveRunPort.PUBMED_IMPORT), any()))
        .thenAnswer(inv -> Optional.ofNullable(((Supplier<?>) inv.getArgument(1)).get()));
    when(batchPort.launchReparse("reparse-" + NOW.toEpochMilli()))
        .thenReturn(PubmedFileCounts.builder().updated(7).build());

    PubmedReparseResult result = handler.handle(new PubmedReparseCommand());

    assertThat(result.skippedByLock()).isFalse();
    assertThat(result.counts().updated()).isEqualTo(7);
    verify(batchPort).launchReparse("reparse-" + NOW.toEpochMilli());
  }

  @Test
  @DisplayName("与文件导入共用一把锁：拿不到锁时跳过")
  void reparse_and_import_share_the_same_lock() {
    when(lock.runExclusively(eq(ExclusiveRunPort.PUBMED_IMPORT), any()))
        .thenReturn(Optional.empty());

    assertThat(handler.handle(new PubmedReparseCommand()).skippedByLock()).isTrue();
    verifyNoInteractions(batchPort);
  }
}
