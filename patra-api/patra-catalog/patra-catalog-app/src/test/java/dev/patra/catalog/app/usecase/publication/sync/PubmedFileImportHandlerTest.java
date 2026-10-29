package dev.patra.catalog.app.usecase.publication.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.app.usecase.publication.sync.command.PubmedFileImportCommand;
import dev.patra.catalog.app.usecase.publication.sync.dto.PubmedFileImportResult;
import dev.patra.catalog.domain.exception.CatalogScheduleParameterException;
import dev.patra.catalog.domain.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.model.enums.PubmedFileStatus;
import dev.patra.catalog.domain.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.model.vo.publication.PubmedFileCounts;
import dev.patra.catalog.domain.model.vo.publication.PubmedFileImportParams;
import dev.patra.catalog.domain.model.vo.publication.PubmedFileRecord;
import dev.patra.catalog.domain.model.vo.publication.PubmedFileRef;
import dev.patra.catalog.domain.port.batch.PublicationBatchPort;
import dev.patra.catalog.domain.port.lock.ExclusiveRunPort;
import dev.patra.catalog.domain.port.repository.PubmedFileRepository;
import dev.patra.catalog.domain.port.source.PubmedFileListingPort;
import dev.patra.tool.core.error.ApplicationException;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

/// `PubmedFileImportHandler` 单元测试。
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PubmedFileImportHandler")
class PubmedFileImportHandlerTest {

  private static final String URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/";
  private static final String BASELINE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";
  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");

  @Mock private PubmedFileRepository progressRepository;
  @Mock private PubmedFileListingPort listingPort;
  @Mock private PublicationBatchPort batchPort;
  @Mock private ExclusiveRunPort exclusiveRunPort;
  @Captor private ArgumentCaptor<PubmedFileImportParams> paramsCaptor;

  private final List<String> events = new ArrayList<>();

  private PubmedFileImportHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new PubmedFileImportHandler(
            progressRepository,
            listingPort,
            batchPort,
            exclusiveRunPort,
            new RecordingTransactions(events),
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(exclusiveRunPort.runExclusively(eq(ExclusiveRunPort.PUBMED_IMPORT), any()))
        .thenAnswer(inv -> Optional.ofNullable(((Supplier<?>) inv.getArgument(1)).get()));
    when(batchPort.launchFileImport(any())).thenReturn(counts(1));
    when(progressRepository.findByFileName(anyString())).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("从最后一个成功文件的下一个开始，处理到最新")
  void should_start_from_last_completed_plus_one() {
    when(progressRepository.findLastCompletedSeq(PubmedFileKind.UPDATE))
        .thenReturn(OptionalInt.of(1335));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1337);

    PubmedFileImportResult result = handler.handle(command(null, null));

    verify(batchPort, times(2)).launchFileImport(paramsCaptor.capture());
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
    when(progressRepository.findFirstUnfinishedSeq(PubmedFileKind.UPDATE))
        .thenReturn(OptionalInt.of(1336));
    when(progressRepository.findLastCompletedSeq(PubmedFileKind.UPDATE))
        .thenReturn(OptionalInt.of(1338));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1338);
    when(progressRepository.findByFileName("pubmed26n1337.xml.gz"))
        .thenReturn(Optional.of(record(PubmedFileStatus.COMPLETED, null)));
    when(progressRepository.findByFileName("pubmed26n1338.xml.gz"))
        .thenReturn(Optional.of(record(PubmedFileStatus.COMPLETED, null)));

    PubmedFileImportResult result = handler.handle(command(null, null));

    verify(batchPort).launchFileImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().file())
        .isEqualTo(PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 1336));
    assertThat(result.filesProcessed()).isEqualTo(1);
    assertThat(result.lastSeq()).isEqualTo(1336);
  }

  @Test
  @DisplayName("进度表为空时从配置的起点开始")
  void should_start_from_configured_seq_when_no_progress() {
    when(progressRepository.findLastCompletedSeq(PubmedFileKind.UPDATE))
        .thenReturn(OptionalInt.empty());
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1335);

    handler.handle(command(null, null));

    verify(batchPort).launchFileImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().file())
        .isEqualTo(PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 1335));
  }

  @Test
  @DisplayName("某个文件失败时标记失败、停止并抛出")
  void should_stop_and_mark_failed_when_file_fails() {
    when(progressRepository.findLastCompletedSeq(PubmedFileKind.UPDATE))
        .thenReturn(OptionalInt.of(1335));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1337);
    when(batchPort.launchFileImport(any())).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> handler.handle(command(null, null)))
        .isInstanceOf(ApplicationException.class);

    verify(progressRepository).markFailed(eq("pubmed26n1336.xml.gz"), anyString(), eq(NOW));
    verify(progressRepository, never())
        .markRunning(
            eq(PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 1337)), any(), any(), any());
  }

  @Test
  @DisplayName("reprocess 时已完成的文件换新的 generation 重放")
  void reprocess_uses_new_generation_for_completed_file() {
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1340);
    when(progressRepository.findByFileName("pubmed26n1340.xml.gz"))
        .thenReturn(Optional.of(record(PubmedFileStatus.COMPLETED, null)));

    handler.handle(command(1340, null));

    verify(batchPort).launchFileImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().generation()).isEqualTo("replay-" + NOW.toEpochMilli());
  }

  @Test
  @DisplayName("重试失败的重放时沿用它记下的 generation，不回落到已完成的原作业实例")
  void should_reuse_stored_generation_when_retrying_failed_replay() {
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1340);
    when(progressRepository.findByFileName("pubmed26n1340.xml.gz"))
        .thenReturn(Optional.of(record(PubmedFileStatus.FAILED, "replay-1")));

    handler.handle(command(1340, null));

    verify(batchPort).launchFileImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().generation()).isEqualTo("replay-1");
    verify(progressRepository)
        .markRunning(
            PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 1340),
            PubmedImportScope.EXISTING_ONLY,
            "replay-1",
            NOW);
  }

  @Test
  @DisplayName("拿不到锁时直接返回跳过")
  void should_return_skipped_when_lock_held() {
    when(exclusiveRunPort.runExclusively(eq(ExclusiveRunPort.PUBMED_IMPORT), any()))
        .thenReturn(Optional.empty());

    PubmedFileImportResult result = handler.handle(command(null, null));

    assertThat(result.skippedByLock()).isTrue();
    verifyNoInteractions(batchPort, listingPort);
  }

  @Test
  @DisplayName("按 maxFiles 截断")
  void should_cap_files_by_max_files() {
    when(progressRepository.findLastCompletedSeq(PubmedFileKind.UPDATE))
        .thenReturn(OptionalInt.of(1335));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1687);

    PubmedFileImportResult result = handler.handle(command(null, 3));

    assertThat(result.filesProcessed()).isEqualTo(3);
    assertThat(result.lastSeq()).isEqualTo(1338);
  }

  @Test
  @DisplayName("已是最新时不处理任何文件")
  void should_do_nothing_when_up_to_date() {
    when(progressRepository.findLastCompletedSeq(PubmedFileKind.UPDATE))
        .thenReturn(OptionalInt.of(1687));
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1687);

    PubmedFileImportResult result = handler.handle(command(null, null));

    assertThat(result.filesProcessed()).isZero();
    assertThat(result.lastSeq()).isNull();
    verifyNoInteractions(batchPort);
  }

  /// UPDATE 命令；`from` 非空时视为显式重处理（旧 `fromFile` 的语义）。
  @Test
  @DisplayName("BASELINE 必须给 from；to 缺省等于 from，只处理这一个文件且不查最新序号")
  void baseline_requires_from_and_defaults_to_equal_from() {
    assertThatThrownBy(
            () ->
                PubmedFileImportCommand.builder()
                    .kind(PubmedFileKind.BASELINE)
                    .baseUrl(BASELINE_URL)
                    .filePrefix("pubmed26n")
                    .startSeq(1335)
                    .scope(PubmedImportScope.EXISTING_ONLY)
                    .build())
        .isInstanceOf(CatalogScheduleParameterException.class);

    handler.handle(
        PubmedFileImportCommand.builder()
            .kind(PubmedFileKind.BASELINE)
            .baseUrl(BASELINE_URL)
            .filePrefix("pubmed26n")
            .startSeq(1335)
            .scope(PubmedImportScope.EXISTING_ONLY)
            .from(1333)
            .reprocess(true)
            .build());

    verify(batchPort).launchFileImport(paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().file())
        .isEqualTo(PubmedFileRef.of(PubmedFileKind.BASELINE, "pubmed26n", 1333));
    assertThat(paramsCaptor.getValue().baseUrl()).isEqualTo(BASELINE_URL);
    verifyNoInteractions(listingPort);
  }

  @Test
  @DisplayName("不带 reprocess 时跳过已完成的文件；带 reprocess 时不跳过")
  void skips_completed_files_unless_reprocess() {
    when(progressRepository.findByFileName("pubmed26n1335.xml.gz"))
        .thenReturn(Optional.of(record(PubmedFileStatus.COMPLETED, null)));
    PubmedFileImportCommand.PubmedFileImportCommandBuilder builder =
        PubmedFileImportCommand.builder()
            .kind(PubmedFileKind.UPDATE)
            .baseUrl(URL)
            .filePrefix("pubmed26n")
            .startSeq(1335)
            .scope(PubmedImportScope.EXISTING_ONLY)
            .from(1335)
            .to(1336);

    assertThat(handler.handle(builder.reprocess(false).build()).filesProcessed()).isEqualTo(1);
    assertThat(handler.handle(builder.reprocess(true).build()).filesProcessed()).isEqualTo(2);
  }

  /// 进度写入各自在短事务里，Spring Batch 作业在事务外启动。
  @Test
  @DisplayName("进度写入各自在短事务里，作业在事务外启动")
  void should_write_progress_in_own_transactions_and_launch_job_outside() {
    recordProgressWrites();
    doAnswer(
            inv -> {
              events.add("launch");
              return counts(1);
            })
        .when(batchPort)
        .launchFileImport(any());

    handler.handle(command(1336, 1));

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN,
            "markRunning",
            RecordingTransactions.END,
            "launch",
            RecordingTransactions.BEGIN,
            "markCompleted",
            RecordingTransactions.END);
  }

  /// 作业失败时在短事务里标记失败，再抛出。
  @Test
  @DisplayName("作业失败时在短事务里标记失败")
  void should_mark_failed_in_own_transaction_when_job_fails() {
    recordProgressWrites();
    when(batchPort.launchFileImport(any())).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> handler.handle(command(1336, 1)))
        .isInstanceOf(ApplicationException.class);

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN,
            "markRunning",
            RecordingTransactions.END,
            RecordingTransactions.BEGIN,
            "markFailed",
            RecordingTransactions.END);
  }

  /// 让三个进度写方法往事件列表里记名字。
  private void recordProgressWrites() {
    when(listingPort.findLatestSeq(URL, "pubmed26n")).thenReturn(1340);
    doAnswer(inv -> events.add("markRunning"))
        .when(progressRepository)
        .markRunning(any(), any(), any(), any());
    doAnswer(inv -> events.add("markCompleted"))
        .when(progressRepository)
        .markCompleted(anyString(), any(), any());
    doAnswer(inv -> events.add("markFailed"))
        .when(progressRepository)
        .markFailed(anyString(), anyString(), any());
  }

  private static PubmedFileImportCommand command(Integer from, Integer maxFiles) {
    return PubmedFileImportCommand.builder()
        .kind(PubmedFileKind.UPDATE)
        .baseUrl(URL)
        .filePrefix("pubmed26n")
        .startSeq(1335)
        .scope(PubmedImportScope.EXISTING_ONLY)
        .from(from)
        .maxFiles(maxFiles)
        .reprocess(from != null)
        .build();
  }

  private static PubmedFileCounts counts(int updated) {
    return PubmedFileCounts.builder().updated(updated).build();
  }

  private static PubmedFileRecord record(PubmedFileStatus status, String generation) {
    return PubmedFileRecord.builder()
        .fileName("pubmed26n1340.xml.gz")
        .fileSeq(1340)
        .status(status)
        .scope(PubmedImportScope.EXISTING_ONLY)
        .counts(PubmedFileCounts.empty())
        .generation(generation)
        .build();
  }
}
