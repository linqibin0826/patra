package dev.patra.catalog.app.usecase.publication.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.app.usecase.publication.update.command.PubmedUpdateSyncCommand;
import dev.patra.catalog.app.usecase.publication.update.dto.PubmedUpdateSyncResult;
import dev.patra.catalog.domain.model.enums.PubmedUpdateFileStatus;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileCounts;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileRecord;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileRef;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateImportParams;
import dev.patra.catalog.domain.port.batch.PublicationBatchPort;
import dev.patra.catalog.domain.port.lock.ExclusiveRunPort;
import dev.patra.catalog.domain.port.repository.PubmedUpdateFileRepository;
import dev.patra.catalog.domain.port.source.PubmedUpdateFileListingPort;
import dev.patra.tool.core.error.ApplicationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/// `PubmedUpdateSyncHandler` 单元测试。
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PubmedUpdateSyncHandler")
class PubmedUpdateSyncHandlerTest {

  private static final String URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/";
  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");

  @Mock private PubmedUpdateFileRepository progressRepository;
  @Mock private PubmedUpdateFileListingPort listingPort;
  @Mock private PublicationBatchPort batchPort;
  @Mock private ExclusiveRunPort exclusiveRunPort;
  @Captor private ArgumentCaptor<PubmedUpdateImportParams> paramsCaptor;

  private PubmedUpdateSyncHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new PubmedUpdateSyncHandler(
            progressRepository,
            listingPort,
            batchPort,
            exclusiveRunPort,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(exclusiveRunPort.runExclusively(eq(ExclusiveRunPort.PUBMED_UPDATE_SYNC), any()))
        .thenAnswer(inv -> Optional.ofNullable(((Supplier<?>) inv.getArgument(1)).get()));
    when(batchPort.launchUpdateImport(any())).thenReturn(counts(1));
    when(progressRepository.findByFileName(anyString())).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("从最后一个成功文件的下一个开始，处理到最新")
  void should_start_from_last_completed_plus_one() {
    when(progressRepository.findLastCompletedSeq()).thenReturn(OptionalInt.of(1335));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1337);

    PubmedUpdateSyncResult result = handler.handle(command(null, null));

    verify(batchPort, times(2)).launchUpdateImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getAllValues())
        .extracting(p -> p.file().seq())
        .containsExactly(1336, 1337);
    assertThat(result.filesProcessed()).isEqualTo(2);
    assertThat(result.lastSeq()).isEqualTo(1337);
    assertThat(result.total().updated()).isEqualTo(2);
    verify(progressRepository).markCompleted("pubmed26n1337.xml.gz", counts(1), NOW);
  }

  @Test
  @DisplayName("有未完成的文件时从最小的那个开始，跳过其后已完成的文件")
  void should_resume_from_first_unfinished_file_and_skip_completed_ones() {
    when(progressRepository.findFirstUnfinishedSeq()).thenReturn(OptionalInt.of(1336));
    when(progressRepository.findLastCompletedSeq()).thenReturn(OptionalInt.of(1338));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1338);
    when(progressRepository.findByFileName("pubmed26n1337.xml.gz"))
        .thenReturn(Optional.of(record(PubmedUpdateFileStatus.COMPLETED, null)));
    when(progressRepository.findByFileName("pubmed26n1338.xml.gz"))
        .thenReturn(Optional.of(record(PubmedUpdateFileStatus.COMPLETED, null)));

    PubmedUpdateSyncResult result = handler.handle(command(null, null));

    verify(batchPort).launchUpdateImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().file()).isEqualTo(PubmedUpdateFileRef.of("pubmed26n", 1336));
    assertThat(result.filesProcessed()).isEqualTo(1);
    assertThat(result.lastSeq()).isEqualTo(1336);
  }

  @Test
  @DisplayName("进度表为空时从配置的起点开始")
  void should_start_from_configured_seq_when_no_progress() {
    when(progressRepository.findLastCompletedSeq()).thenReturn(OptionalInt.empty());
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1335);

    handler.handle(command(null, null));

    verify(batchPort).launchUpdateImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().file()).isEqualTo(PubmedUpdateFileRef.of("pubmed26n", 1335));
  }

  @Test
  @DisplayName("某个文件失败时标记失败、停止并抛出")
  void should_stop_and_mark_failed_when_file_fails() {
    when(progressRepository.findLastCompletedSeq()).thenReturn(OptionalInt.of(1335));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1337);
    when(batchPort.launchUpdateImport(any())).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> handler.handle(command(null, null)))
        .isInstanceOf(ApplicationException.class);

    verify(progressRepository).markFailed(eq("pubmed26n1336.xml.gz"), anyString(), eq(NOW));
    verify(progressRepository, never())
        .markRunning(eq(PubmedUpdateFileRef.of("pubmed26n", 1337)), any(), any(), any());
  }

  @Test
  @DisplayName("fromFile 指定的文件已完成过时换新的 generation 重放")
  void should_replay_with_new_generation_when_from_file_already_completed() {
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1340);
    when(progressRepository.findByFileName("pubmed26n1340.xml.gz"))
        .thenReturn(Optional.of(record(PubmedUpdateFileStatus.COMPLETED, null)));

    handler.handle(command(1340, null));

    verify(batchPort).launchUpdateImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().generation()).isEqualTo("replay-" + NOW.toEpochMilli());
  }

  @Test
  @DisplayName("重试失败的重放时沿用它记下的 generation，不回落到已完成的原作业实例")
  void should_reuse_stored_generation_when_retrying_failed_replay() {
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1340);
    when(progressRepository.findByFileName("pubmed26n1340.xml.gz"))
        .thenReturn(Optional.of(record(PubmedUpdateFileStatus.FAILED, "replay-1")));

    handler.handle(command(1340, null));

    verify(batchPort).launchUpdateImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().generation()).isEqualTo("replay-1");
    verify(progressRepository)
        .markRunning(
            PubmedUpdateFileRef.of("pubmed26n", 1340),
            PubmedUpdateScope.EXISTING_ONLY,
            "replay-1",
            NOW);
  }

  @Test
  @DisplayName("拿不到锁时直接返回跳过")
  void should_return_skipped_when_lock_held() {
    when(exclusiveRunPort.runExclusively(eq(ExclusiveRunPort.PUBMED_UPDATE_SYNC), any()))
        .thenReturn(Optional.empty());

    PubmedUpdateSyncResult result = handler.handle(command(null, null));

    assertThat(result.skippedByLock()).isTrue();
    verifyNoInteractions(batchPort, listingPort);
  }

  @Test
  @DisplayName("按 maxFiles 截断")
  void should_cap_files_by_max_files() {
    when(progressRepository.findLastCompletedSeq()).thenReturn(OptionalInt.of(1335));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1687);

    PubmedUpdateSyncResult result = handler.handle(command(null, 3));

    assertThat(result.filesProcessed()).isEqualTo(3);
    assertThat(result.lastSeq()).isEqualTo(1338);
  }

  @Test
  @DisplayName("已是最新时不处理任何文件")
  void should_do_nothing_when_up_to_date() {
    when(progressRepository.findLastCompletedSeq()).thenReturn(OptionalInt.of(1687));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1687);

    PubmedUpdateSyncResult result = handler.handle(command(null, null));

    assertThat(result.filesProcessed()).isZero();
    assertThat(result.lastSeq()).isNull();
    verifyNoInteractions(batchPort);
  }

  private static PubmedUpdateSyncCommand command(Integer fromFile, Integer maxFiles) {
    return PubmedUpdateSyncCommand.builder()
        .updateUrl(URL)
        .filePrefix("pubmed26n")
        .startSeq(1335)
        .scope(PubmedUpdateScope.EXISTING_ONLY)
        .fromFile(fromFile)
        .maxFiles(maxFiles)
        .build();
  }

  private static PubmedUpdateFileCounts counts(int updated) {
    return PubmedUpdateFileCounts.builder().updated(updated).build();
  }

  private static PubmedUpdateFileRecord record(PubmedUpdateFileStatus status, String generation) {
    return PubmedUpdateFileRecord.builder()
        .fileName("pubmed26n1340.xml.gz")
        .fileSeq(1340)
        .status(status)
        .scope(PubmedUpdateScope.EXISTING_ONLY)
        .counts(PubmedUpdateFileCounts.empty())
        .generation(generation)
        .build();
  }
}
