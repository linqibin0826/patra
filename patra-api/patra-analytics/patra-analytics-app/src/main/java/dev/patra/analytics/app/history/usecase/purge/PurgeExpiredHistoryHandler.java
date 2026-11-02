package dev.patra.analytics.app.history.usecase.purge;

import dev.patra.analytics.domain.history.policy.HistoryRetention;
import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.tool.cqrs.CommandHandler;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.function.IntUnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 过期清理（spec §7.1）：删 12 个日历月前的历史、2 天前的删除记录。每批 {@value HistoryRetention#PURGE_BATCH} 行、
/// 一条语句自成一个短事务，删到某批不满为止，不长时间锁表。正好落在过期线上的保留。删除幂等，失败次日重跑。
@Component
@RequiredArgsConstructor
public class PurgeExpiredHistoryHandler
    implements CommandHandler<PurgeExpiredHistoryCommand, PurgeResult> {

  /// 过期清理删掉的行数（标签 `table`：`reading_history`、`deleted_entry`）
  static final String PURGED_METRIC = "analytics.history.purged";

  private final ReadingHistoryRepository history;
  private final Clock clock;
  private final MeterRegistry meters;

  /// 清理。
  ///
  /// @param command 命令
  /// @return 删掉的行数
  @Override
  public PurgeResult handle(PurgeExpiredHistoryCommand command) {
    Instant now = clock.instant();
    Instant historyCutoff = HistoryRetention.historyCutoff(now);
    Instant deletionCutoff = HistoryRetention.deletionCutoff(now);
    int historyRows =
        purge("reading_history", limit -> history.purgeReadBefore(historyCutoff, limit));
    int deletionRows =
        purge("deleted_entry", limit -> history.purgeDeletionsBefore(deletionCutoff, limit));
    return PurgeResult.of(historyRows, deletionRows);
  }

  /// 一批一批删到不满一批。
  ///
  /// @param table 表名（指标标签）
  /// @param batch 删一批，参数是上限，返回删掉的行数
  /// @return 合计
  private int purge(String table, IntUnaryOperator batch) {
    int total = 0;
    int deleted;
    do {
      deleted = batch.applyAsInt(HistoryRetention.PURGE_BATCH);
      total += deleted;
    } while (deleted == HistoryRetention.PURGE_BATCH);
    meters.counter(PURGED_METRIC, "table", table).increment(total);
    return total;
  }
}
