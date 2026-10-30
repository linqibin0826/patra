package dev.patra.interaction.app.subject.usecase.recount;

import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.port.gateway.StatsRecountGateway;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 按明细重算的公共流程（release spec v0.15 决策 A）：按计数名逐项，先找差异，再每个差异键开一个短事务让子域
/// 「先锁、再数、不一致才写」，汇总每项真改了的行数。
///
/// 不加任务级锁：只手动触发，调度中心「单机串行」。子域抛出的意外异常不吞，让任务失败、标红。
@Slf4j
@Component
@RequiredArgsConstructor
public class RecountStatsHandler implements CommandHandler<RecountStatsCommand, RecountResult> {

  private final List<StatsRecountGateway<?>> counters;
  private final TransactionOperations transactions;

  /// 重算。
  ///
  /// @param command 命令
  /// @return 每项修正的行数
  @Override
  public RecountResult handle(RecountStatsCommand command) {
    RecountResult result =
        RecountResult.of(
            counters.stream()
                .sorted(Comparator.comparing(StatsRecountGateway::counter))
                .map(counter -> CounterResult.of(counter.counter(), recountAll(counter)))
                .toList());
    log.info("统计重算完成: corrected={}, byCounter={}", result.corrected(), result.summary());
    return result;
  }

  /// 一项计数：找差异，逐键在各自的事务里修正。
  ///
  /// @param counter 计数
  /// @param <K> 键类型
  /// @return 修正的行数
  private <K> int recountAll(StatsRecountGateway<K> counter) {
    int corrected = 0;
    for (K key : counter.findDrifted()) {
      Optional<StatsCorrection> correction = transactions.execute(status -> counter.recount(key));
      if (correction != null && correction.isPresent()) {
        corrected++;
        log.info(
            "统计重算修正: counter={}, key={}, before={}, after={}",
            counter.counter(),
            key,
            correction.get().before(),
            correction.get().after());
      }
    }
    return corrected;
  }
}
