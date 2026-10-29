package dev.patra.catalog.app.publication.usecase.sync;

import dev.patra.catalog.domain.kernel.port.lock.ExclusiveRunPort;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.port.batch.PublicationBatchPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 本地重算：解析器升级后按来源表里的原文重算全部未软删的文献，不访问网络（spec 第 5.5 节）。
///
/// 与文件导入共用一把任务锁；每次一个新的作业 generation。不加 `@Transactional`：Spring Batch 不能在外层事务里启动。
///
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
public class PubmedReparseHandler
    implements CommandHandler<PubmedReparseCommand, PubmedReparseResult> {

  private final PublicationBatchPort publicationBatchPort;
  private final ExclusiveRunPort exclusiveRunPort;
  private final Clock clock;

  /// 执行一次本地重算。
  ///
  /// @param command 命令
  /// @return 结果；另一个实例在运行时为跳过
  @Override
  public PubmedReparseResult handle(PubmedReparseCommand command) {
    return exclusiveRunPort
        .runExclusively(
            ExclusiveRunPort.PUBMED_IMPORT,
            () ->
                PubmedReparseResult.of(
                    publicationBatchPort.launchReparse("reparse-" + clock.millis()), false))
        .orElseGet(() -> PubmedReparseResult.of(PubmedFileCounts.empty(), true));
  }
}
