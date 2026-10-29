package dev.patra.catalog.app.usecase.publication.sync;

import dev.patra.catalog.api.error.CatalogErrorCode;
import dev.patra.catalog.app.usecase.publication.sync.command.PubmedFileImportCommand;
import dev.patra.catalog.app.usecase.publication.sync.dto.PubmedFileImportResult;
import dev.patra.catalog.domain.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.model.enums.PubmedFileStatus;
import dev.patra.catalog.domain.model.vo.publication.PubmedFileCounts;
import dev.patra.catalog.domain.model.vo.publication.PubmedFileImportParams;
import dev.patra.catalog.domain.model.vo.publication.PubmedFileRef;
import dev.patra.catalog.domain.port.batch.PublicationBatchPort;
import dev.patra.catalog.domain.port.lock.ExclusiveRunPort;
import dev.patra.catalog.domain.port.repository.PubmedFileRepository;
import dev.patra.catalog.domain.port.source.PubmedFileListingPort;
import dev.patra.tool.core.error.ApplicationException;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 处理一段 PubMed 文件（baseline 或 updatefiles），按序号依次处理（spec 第 5.8 节）。
///
/// - 起点：给了 `from` 用它；UPDATE 不给时从 UPDATE 进度表最小的未完成文件续跑，没有则最后一个成功文件的下一个，
///   进度表为空时用配置的起点（只看 UPDATE 行：baseline 与 update 共用编号，失败的 baseline 行不能挡住每日增量）
/// - 终点：给了 `to` 用它；BASELINE 缺省等于起点；UPDATE 缺省为最新；再按 `maxFiles` 截断
/// - `reprocess = false` 跳过已完成的文件；`true` 不跳过，已完成的文件换新的作业 generation（生成新的作业实例）
///
/// 遇到失败的文件就标记失败并停止（下次运行从它重试）。整次运行包在与本地重算共用的任务锁里。
/// 不整体包事务：Spring Batch 不能在外层事务里启动；进度表的每次写入各用 `TransactionOperations` 包一个短事务。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PubmedFileImportHandler
    implements CommandHandler<PubmedFileImportCommand, PubmedFileImportResult> {

  private final PubmedFileRepository progressRepository;
  private final PubmedFileListingPort listingPort;
  private final PublicationBatchPort publicationBatchPort;
  private final ExclusiveRunPort exclusiveRunPort;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 执行一次。
  ///
  /// @param command 命令
  /// @return 结果；另一个实例在运行时为跳过
  /// @throws ApplicationException 某个文件处理失败时抛出（错误码 `CAT_1501`）
  @Override
  public PubmedFileImportResult handle(PubmedFileImportCommand command) {
    return exclusiveRunPort
        .runExclusively(ExclusiveRunPort.PUBMED_IMPORT, () -> run(command))
        .orElseGet(PubmedFileImportResult::ofSkippedByLock);
  }

  /// 按序号依次处理。
  ///
  /// @param command 命令
  /// @return 结果
  private PubmedFileImportResult run(PubmedFileImportCommand command) {
    int start = command.from() != null ? command.from() : resumeSeq(command);
    int end = endSeq(command, start);
    if (command.maxFiles() != null) {
      end = Math.min(end, start + command.maxFiles() - 1);
    }
    log.info(
        "PubMed 导入范围：{} {}{} → {}，scope={}，reprocess={}",
        command.kind(),
        command.filePrefix(),
        start,
        end,
        command.scope(),
        command.reprocess());

    PubmedFileCounts total = PubmedFileCounts.empty();
    int processed = 0;
    Integer lastSeq = null;
    for (int seq = start; seq <= end; seq++) {
      PubmedFileRef file = PubmedFileRef.of(command.kind(), command.filePrefix(), seq);
      if (!command.reprocess() && isCompleted(file)) {
        continue;
      }
      total = total.plus(processFile(command, file));
      processed++;
      lastSeq = seq;
    }
    return PubmedFileImportResult.of(processed, lastSeq, total, false);
  }

  /// 终点：`to` 优先；BASELINE 缺省等于起点；UPDATE 缺省为最新。
  ///
  /// @param command 命令
  /// @param start 起点
  /// @return 终点（含）
  private int endSeq(PubmedFileImportCommand command, int start) {
    if (command.to() != null) {
      return command.to();
    }
    if (command.kind() == PubmedFileKind.BASELINE) {
      return start;
    }
    return listingPort.findLatestSeq(command.baseUrl(), command.filePrefix());
  }

  /// UPDATE 的续跑起点：最小的未完成文件；没有则最后一个成功文件的下一个；进度表为空时用配置的起点。
  ///
  /// @param command 命令
  /// @return 起始文件号
  private int resumeSeq(PubmedFileImportCommand command) {
    OptionalInt unfinished = progressRepository.findFirstUnfinishedSeq(PubmedFileKind.UPDATE);
    if (unfinished.isPresent()) {
      return unfinished.getAsInt();
    }
    OptionalInt lastCompleted = progressRepository.findLastCompletedSeq(PubmedFileKind.UPDATE);
    return lastCompleted.isPresent() ? lastCompleted.getAsInt() + 1 : command.startSeq();
  }

  /// 文件是否已处理成功。
  ///
  /// @param file 文件
  /// @return 是否
  private boolean isCompleted(PubmedFileRef file) {
    return progressRepository
        .findByFileName(file.fileName())
        .map(r -> r.status() == PubmedFileStatus.COMPLETED)
        .orElse(false);
  }

  /// 处理一个文件：标记运行 → 启动作业 → 标记完成；失败时标记失败并抛出。
  ///
  /// @param command 命令
  /// @param file 文件
  /// @return 计数
  private PubmedFileCounts processFile(PubmedFileImportCommand command, PubmedFileRef file) {
    String generation = generationFor(command, file);
    Instant startedAt = clock.instant();
    transactions.executeWithoutResult(
        status -> progressRepository.markRunning(file, command.scope(), generation, startedAt));
    try {
      PubmedFileCounts counts =
          publicationBatchPort.launchFileImport(
              PubmedFileImportParams.of(file, command.baseUrl(), command.scope(), generation));
      Instant finishedAt = clock.instant();
      transactions.executeWithoutResult(
          status -> progressRepository.markCompleted(file.fileName(), counts, finishedAt));
      log.info("PubMed 文件完成：{}，{}", file.fileName(), counts);
      return counts;
    } catch (RuntimeException e) {
      Instant failedAt = clock.instant();
      transactions.executeWithoutResult(
          status -> progressRepository.markFailed(file.fileName(), e.getMessage(), failedAt));
      throw new ApplicationException(
          CatalogErrorCode.CAT_1501, "PubMed 文件处理失败：" + file.fileName() + "，" + e.getMessage(), e);
    }
  }

  /// 决定这次处理所用的作业 generation。
  ///
  /// 重处理已完成的文件时换新的 generation，生成新的作业实例；未完成的文件沿用上次记下的 generation，
  /// 续跑真正失败的那个实例（否则失败的重放会回落到已完成的原实例而被跳过）；从未处理过的文件为 `null`。
  ///
  /// @param command 命令
  /// @param file 文件
  /// @return generation，可空
  private String generationFor(PubmedFileImportCommand command, PubmedFileRef file) {
    return progressRepository
        .findByFileName(file.fileName())
        .map(
            r ->
                command.reprocess() && r.status() == PubmedFileStatus.COMPLETED
                    ? "replay-" + clock.millis()
                    : r.generation())
        .orElse(null);
  }
}
