package dev.patra.catalog.app.usecase.publication.update;

import dev.patra.catalog.api.error.CatalogErrorCode;
import dev.patra.catalog.app.usecase.publication.update.command.PubmedUpdateSyncCommand;
import dev.patra.catalog.app.usecase.publication.update.dto.PubmedUpdateSyncResult;
import dev.patra.catalog.domain.model.enums.PubmedUpdateFileStatus;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileCounts;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileRef;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateImportParams;
import dev.patra.catalog.domain.port.batch.PublicationBatchPort;
import dev.patra.catalog.domain.port.lock.ExclusiveRunPort;
import dev.patra.catalog.domain.port.repository.PubmedUpdateFileRepository;
import dev.patra.catalog.domain.port.source.PubmedUpdateFileListingPort;
import dev.patra.tool.core.error.ApplicationException;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed updatefiles 增量同步：从进度表最小的未完成文件（没有则最后一个成功文件的下一个）开始，
/// 按文件号依次处理到最新，跳过其间已完成的文件。
///
/// 遇到失败的文件就标记失败并停止（下次运行从它重试）。整次运行包在任务互斥锁里。
/// 不加 `@Transactional`：Spring Batch 不能在外层事务里启动，进度仓储的写方法自带事务。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PubmedUpdateSyncHandler
    implements CommandHandler<PubmedUpdateSyncCommand, PubmedUpdateSyncResult> {

  private final PubmedUpdateFileRepository progressRepository;
  private final PubmedUpdateFileListingPort listingPort;
  private final PublicationBatchPort publicationBatchPort;
  private final ExclusiveRunPort exclusiveRunPort;
  private final Clock clock;

  /// 执行一次增量同步。
  ///
  /// @param command 命令
  /// @return 结果；另一个实例在运行时为跳过
  /// @throws ApplicationException 某个文件处理失败时抛出（错误码 `CAT_1501`）
  @Override
  public PubmedUpdateSyncResult handle(PubmedUpdateSyncCommand command) {
    return exclusiveRunPort
        .runExclusively(ExclusiveRunPort.PUBMED_UPDATE_SYNC, () -> sync(command))
        .orElseGet(PubmedUpdateSyncResult::ofSkippedByLock);
  }

  /// 按文件号依次处理。
  private PubmedUpdateSyncResult sync(PubmedUpdateSyncCommand command) {
    int start = command.fromFile() != null ? command.fromFile() : resumeSeq(command);
    int latest = listingPort.findLatestSeq(command.updateUrl(), command.filePrefix());
    int end =
        command.maxFiles() != null ? Math.min(latest, start + command.maxFiles() - 1) : latest;
    log.info(
        "增量同步范围：{}{} → {}（最新 {}），scope={}",
        command.filePrefix(),
        start,
        end,
        latest,
        command.scope());

    PubmedUpdateFileCounts total = PubmedUpdateFileCounts.empty();
    int processed = 0;
    Integer lastSeq = null;
    for (int seq = start; seq <= end; seq++) {
      PubmedUpdateFileRef file = PubmedUpdateFileRef.of(command.filePrefix(), seq);
      if (command.fromFile() == null && isCompleted(file)) {
        continue;
      }
      PubmedUpdateFileCounts counts = processFile(command, file);
      total = total.plus(counts);
      processed++;
      lastSeq = seq;
    }
    return PubmedUpdateSyncResult.of(processed, lastSeq, total, false);
  }

  /// 续跑起点：最小的未完成文件；没有则最后一个成功文件的下一个；进度表为空时用配置的起点。
  ///
  /// @param command 命令
  /// @return 起始文件号
  private int resumeSeq(PubmedUpdateSyncCommand command) {
    OptionalInt unfinished = progressRepository.findFirstUnfinishedSeq();
    if (unfinished.isPresent()) {
      return unfinished.getAsInt();
    }
    OptionalInt lastCompleted = progressRepository.findLastCompletedSeq();
    return lastCompleted.isPresent() ? lastCompleted.getAsInt() + 1 : command.startSeq();
  }

  /// 文件是否已处理成功。
  ///
  /// @param file 文件
  /// @return 是否
  private boolean isCompleted(PubmedUpdateFileRef file) {
    return progressRepository
        .findByFileName(file.fileName())
        .map(r -> r.status() == PubmedUpdateFileStatus.COMPLETED)
        .orElse(false);
  }

  /// 处理一个文件：标记运行 → 启动作业 → 标记完成；失败时标记失败并抛出。
  private PubmedUpdateFileCounts processFile(
      PubmedUpdateSyncCommand command, PubmedUpdateFileRef file) {
    String generation = replayGeneration(command, file);
    progressRepository.markRunning(file, command.scope(), generation, clock.instant());
    try {
      PubmedUpdateFileCounts counts =
          publicationBatchPort.launchUpdateImport(
              PubmedUpdateImportParams.of(file, command.updateUrl(), command.scope(), generation));
      progressRepository.markCompleted(file.fileName(), counts, clock.instant());
      log.info("增量同步文件完成：{}，{}", file.fileName(), counts);
      return counts;
    } catch (RuntimeException e) {
      progressRepository.markFailed(file.fileName(), e.getMessage(), clock.instant());
      throw new ApplicationException(
          CatalogErrorCode.CAT_1501,
          "PubMed 增量同步文件失败：" + file.fileName() + "，" + e.getMessage(),
          e);
    }
  }

  /// 决定这次处理所用的作业 generation。
  ///
  /// `fromFile` 重放已完成的文件时换新的 generation，生成新的作业实例；未完成的文件沿用上次
  /// 记下的 generation，续跑真正失败的那个实例（否则失败的重放会回落到已完成的原实例而被跳过）；
  /// 从未处理过的文件为 `null`。
  ///
  /// @param command 命令
  /// @param file 文件
  /// @return generation，可空
  private String replayGeneration(PubmedUpdateSyncCommand command, PubmedUpdateFileRef file) {
    return progressRepository
        .findByFileName(file.fileName())
        .map(
            r ->
                command.fromFile() != null && r.status() == PubmedUpdateFileStatus.COMPLETED
                    ? "replay-" + clock.millis()
                    : r.generation())
        .orElse(null);
  }
}
