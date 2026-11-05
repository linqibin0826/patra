package dev.patra.analytics.app.history.listener;

import dev.patra.analytics.domain.collect.event.ArticleRead;
import dev.patra.analytics.domain.collect.event.ArticlesReadEvent;
import dev.patra.analytics.domain.history.model.vo.ReadingKey;
import dev.patra.analytics.domain.history.model.vo.ReadingRecord;
import dev.patra.analytics.domain.history.model.vo.RecordingSetting;
import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 写阅读历史（spec §4.2）：在采集请求的线程里同步接住 {@link ArticlesReadEvent}，按（用户，文献）合并、按开关与清空
/// 时刻、删除记录过滤后一条语句写入。
///
/// 查设置、查删除记录与写入放在同一个事务里、先限时：每条语句与锁等待各 500 ms，只占一个连接（Hikari 拿连接也限
/// 500 ms），PG 卡住时写历史在 1 秒多内失败，不拖过门户 6 秒的超时。事务里只有这三步数据库操作。
///
/// 写失败（含超时）不往外抛：采集照样返回 202，代价是这次打开没进历史，只记 WARN 与 `failed` 指标。
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadingHistoryListener {

  /// 写历史的结果（标签 `outcome`：`recorded`、`disabled`、`erased`、`failed`），按合并后的行数计
  static final String WRITES_METRIC = "analytics.history.writes";

  /// 合并与写入的顺序：两个并发请求按同一顺序加行锁，不会死锁
  private static final Comparator<ReadingKey> KEY_ORDER =
      Comparator.comparingLong(ReadingKey::userId).thenComparingLong(ReadingKey::articleId);

  private final ReadingHistoryRepository history;
  private final RecordingSettingRepository settings;
  private final TransactionOperations transactions;
  private final MeterRegistry meters;

  /// 写一批阅读。
  ///
  /// @param event 采集写成后的阅读事件
  @EventListener
  public void on(ArticlesReadEvent event) {
    List<ReadingRecord> merged = merge(event.reads());
    Tally tally = new Tally(merged.size());
    try {
      transactions.executeWithoutResult(status -> write(merged, tally));
      count("recorded", tally.pending);
    } catch (RuntimeException e) {
      log.warn("写阅读历史失败，这批不记: rows={}, {}", tally.pending, e.toString());
      count("failed", tally.pending);
    }
    count("disabled", tally.disabled);
    count("erased", tally.erased);
  }

  /// 限时 → 查设置与删除记录 → 过滤 → 写入。在事务里执行。
  ///
  /// @param merged 合并后的记录
  /// @param tally 各结果的行数（过滤后更新待写行数）
  private void write(List<ReadingRecord> merged, Tally tally) {
    history.limitStatementTime();
    Set<Long> userIds =
        merged.stream().map(ReadingRecord::userId).collect(Collectors.toUnmodifiableSet());
    Map<Long, RecordingSetting> byUser = settings.findAll(userIds);
    Map<ReadingKey, Instant> deletions =
        history.findDeletedAt(merged.stream().map(ReadingRecord::key).toList());
    List<ReadingRecord> kept = new ArrayList<>(merged.size());
    int disabled = 0;
    int erased = 0;
    for (ReadingRecord record : merged) {
      RecordingSetting setting =
          byUser.getOrDefault(record.userId(), RecordingSetting.defaults(record.userId()));
      Instant deletedAt = deletions.get(record.key());
      if (!setting.enabled()) {
        disabled++;
      } else if (!setting.accepts(record.readAt())
          || (deletedAt != null && !record.readAt().isAfter(deletedAt))) {
        erased++;
      } else {
        kept.add(record);
      }
    }
    tally.disabled = disabled;
    tally.erased = erased;
    tally.pending = kept.size();
    if (!kept.isEmpty()) {
      history.upsertAll(kept);
    }
  }

  /// 按（用户，文献）合并：时间取最晚、全文取「或」；结果按键升序。
  ///
  /// @param reads 未合并的阅读
  /// @return 合并后的记录
  private static List<ReadingRecord> merge(List<ArticleRead> reads) {
    TreeMap<ReadingKey, ReadingRecord> merged = new TreeMap<>(KEY_ORDER);
    for (ArticleRead read : reads) {
      ReadingRecord record =
          ReadingRecord.of(read.userId(), read.articleId(), read.readAt(), read.fulltext());
      merged.merge(
          record.key(),
          record,
          (a, b) ->
              ReadingRecord.of(
                  a.userId(),
                  a.articleId(),
                  a.readAt().isAfter(b.readAt()) ? a.readAt() : b.readAt(),
                  a.fulltext() || b.fulltext()));
    }
    return List.copyOf(merged.values());
  }

  /// 记写历史的结果。
  ///
  /// @param outcome 结果
  /// @param rows 行数，为 0 时不记
  private void count(String outcome, int rows) {
    if (rows > 0) {
      meters.counter(WRITES_METRIC, "outcome", outcome).increment(rows);
    }
  }

  /// 一批写历史的计数。
  private static final class Tally {

    /// 待写的行数：过滤前是合并后的行数，过滤后是要写入的行数
    private int pending;

    /// 因开关关着跳过的行数
    private int disabled;

    /// 发生在清空或删除之前而丢掉的行数
    private int erased;

    /// 创建计数。
    ///
    /// @param merged 合并后的行数
    private Tally(int merged) {
      this.pending = merged;
    }
  }
}
