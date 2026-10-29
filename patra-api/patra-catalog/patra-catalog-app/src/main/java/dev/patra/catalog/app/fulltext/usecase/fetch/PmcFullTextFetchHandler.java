package dev.patra.catalog.app.fulltext.usecase.fetch;

import dev.patra.catalog.domain.kernel.port.lock.ExclusiveRunPort;
import dev.patra.tool.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PMC 全文抓取：整次运行包在任务互斥锁里，另一个实例在运行时直接返回空统计。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PmcFullTextFetchHandler
    implements CommandHandler<PmcFullTextFetchCommand, FullTextFetchRunStats> {

  private final PmcFullTextFetchRunner runner;
  private final ExclusiveRunPort exclusiveRunPort;

  /// 执行一次抓取。
  ///
  /// @param command 命令
  /// @return 统计；拿不到锁时为空统计
  @Override
  public FullTextFetchRunStats handle(PmcFullTextFetchCommand command) {
    return exclusiveRunPort
        .runExclusively(ExclusiveRunPort.FULLTEXT_FETCH, () -> runner.run(command.publicationIds()))
        .orElseGet(
            () -> {
              log.info("另一个全文抓取实例正在运行，本次跳过");
              return FullTextFetchRunStats.empty();
            });
  }
}
