package dev.patra.analytics.app.insight.usecase.coread;

import dev.patra.analytics.domain.insight.model.vo.CoReadingEntry;
import dev.patra.analytics.domain.insight.model.vo.CoReadingPair;
import dev.patra.analytics.domain.insight.policy.CoReadingRule;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import dev.patra.analytics.domain.insight.port.repository.CoReadingSnapshotRepository;
import dev.patra.analytics.domain.insight.port.signal.ReadingSignalPort;
import dev.patra.tool.cqrs.CommandHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 重算共现快照（insight spec §6.2）：ClickHouse 按文献分片算配对 → 结果里出现过的全部文献一次交给 catalog 核对 →
/// 每篇留前 20 名 → 一个事务里整表替换。任何一步失败都原样抛出，旧快照不动，次日再算。
@Component
@RequiredArgsConstructor
public class RefreshCoReadingHandler
    implements CommandHandler<RefreshCoReadingCommand, RefreshCoReadingResult> {

  private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
  private static final String ROWS_METRIC = "analytics.insight.snapshot.rows";
  private static final String JOB = "co_reading";

  private final ReadingSignalPort signals;
  private final PublicationCatalogPort catalog;
  private final CoReadingSnapshotRepository snapshots;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final MeterRegistry meters;
  /// 最近一次写入的快照行数（gauge 读它；第一次成功写入时登记，重复登记返回同一个）。
  private final AtomicInteger snapshotRows = new AtomicInteger();

  /// 重算一次。
  ///
  /// @param command 命令
  /// @return 文献篇数与推荐条数
  @Override
  public RefreshCoReadingResult handle(RefreshCoReadingCommand command) {
    Timer.Sample sample = Timer.start(meters);
    String outcome = "failure";
    try {
      LocalDate today = LocalDate.now(clock.withZone(BEIJING));
      List<CoReadingPair> pairs = signals.coReadingPairs(today, CoReadingRule.CANDIDATE_LIMIT);
      Set<Long> ids = new HashSet<>();
      pairs.forEach(
          p -> {
            ids.add(p.articleId());
            ids.add(p.relatedArticleId());
          });
      Set<Long> eligible = ids.isEmpty() ? Set.of() : catalog.eligibleAmong(ids);
      List<CoReadingEntry> entries = CoReadingRule.keep(pairs, eligible);
      transactions.executeWithoutResult(status -> snapshots.replace(entries, clock.instant()));
      meters.gauge(ROWS_METRIC, Tags.of("kind", JOB), snapshotRows).set(entries.size());
      outcome = "success";
      int articles = (int) entries.stream().map(CoReadingEntry::articleId).distinct().count();
      return RefreshCoReadingResult.of(articles, entries.size());
    } finally {
      sample.stop(meters.timer("analytics.insight.job", "job", JOB, "outcome", outcome));
    }
  }
}
