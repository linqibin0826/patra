package dev.patra.analytics.app.insight.usecase.hot;

import dev.patra.analytics.domain.insight.model.vo.HotReadingCandidate;
import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;
import dev.patra.analytics.domain.insight.policy.HotReadingRule;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import dev.patra.analytics.domain.insight.port.repository.HotReadingSnapshotRepository;
import dev.patra.analytics.domain.insight.port.signal.ReadingSignalPort;
import dev.patra.tool.cqrs.CommandHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 重算热读快照（insight spec §5.3）：ClickHouse 算候选 → catalog 核对去掉撤稿、软删 → 上榜不足一页时取最新收录补位 →
/// 一个事务里整表替换。任何一步失败都原样抛出，旧快照不动，下一个小时再算。
///
/// 远程调用（ClickHouse、catalog）都在事务外，事务里只有写快照。
@Component
@RequiredArgsConstructor
public class RefreshHotReadingHandler
    implements CommandHandler<RefreshHotReadingCommand, RefreshHotReadingResult> {

  private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
  private static final String ROWS_METRIC = "analytics.insight.snapshot.rows";
  private static final String JOB = "hot_reading";

  private final ReadingSignalPort signals;
  private final PublicationCatalogPort catalog;
  private final HotReadingSnapshotRepository snapshots;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final MeterRegistry meters;
  /// 最近一次写入的快照行数（gauge 读它；第一次成功写入时登记，重复登记返回同一个）。
  private final AtomicInteger snapshotRows = new AtomicInteger();

  /// 重算一次。
  ///
  /// @param command 命令
  /// @return 上榜与补位篇数
  @Override
  public RefreshHotReadingResult handle(RefreshHotReadingCommand command) {
    Timer.Sample sample = Timer.start(meters);
    String outcome = "failure";
    try {
      LocalDate today = LocalDate.now(clock.withZone(BEIJING));
      List<HotReadingCandidate> candidates =
          signals.hotCandidates(today, HotReadingRule.CANDIDATE_LIMIT);
      Set<Long> eligible =
          catalog.eligibleAmong(candidates.stream().map(HotReadingCandidate::articleId).toList());
      List<HotReadingCandidate> ranked =
          candidates.stream().filter(c -> eligible.contains(c.articleId())).toList();
      List<Long> latest =
          ranked.size() < HotReadingRule.PAGE_SIZE
              ? catalog.latestIds(HotReadingRule.LATEST_FETCH)
              : List.of();
      List<HotReadingEntry> board = HotReadingRule.board(ranked, latest);
      transactions.executeWithoutResult(status -> snapshots.replace(board, clock.instant()));
      meters.gauge(ROWS_METRIC, Tags.of("kind", JOB), snapshotRows).set(board.size());
      outcome = "success";
      int rankedCount = (int) board.stream().filter(HotReadingEntry::ranked).count();
      return RefreshHotReadingResult.of(rankedCount, board.size() - rankedCount);
    } finally {
      sample.stop(meters.timer("analytics.insight.job", "job", JOB, "outcome", outcome));
    }
  }
}
