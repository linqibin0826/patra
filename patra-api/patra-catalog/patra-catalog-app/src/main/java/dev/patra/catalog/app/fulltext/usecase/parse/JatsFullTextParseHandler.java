package dev.patra.catalog.app.fulltext.usecase.parse;

import dev.patra.catalog.domain.kernel.port.lock.ExclusiveRunPort;
import dev.patra.tool.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// JATS 全文解析：整次运行包在任务互斥锁里，另一个实例在运行时直接返回空统计。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class JatsFullTextParseHandler
    implements CommandHandler<JatsFullTextParseCommand, FullTextParseRunStats> {

  private final JatsFullTextParseRunner runner;
  private final ExclusiveRunPort exclusiveRunPort;

  /// 执行一次解析。
  ///
  /// @param command 命令
  /// @return 统计；拿不到锁时为空统计
  @Override
  public FullTextParseRunStats handle(JatsFullTextParseCommand command) {
    return exclusiveRunPort
        .runExclusively(ExclusiveRunPort.FULLTEXT_PARSE, runner::run)
        .orElseGet(
            () -> {
              log.info("另一个全文解析实例正在运行，本次跳过");
              return FullTextParseRunStats.empty();
            });
  }
}
