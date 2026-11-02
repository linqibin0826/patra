package dev.patra.analytics.infra.insight.persistence;

import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;
import dev.patra.analytics.domain.insight.port.repository.HotReadingSnapshotRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `HotReadingSnapshotRepository` 的原生 SQL 实现：`DELETE` 全表 + `unnest` 批量插入，要求调用方的事务。
///
/// 不用 `TRUNCATE`：它要拿排他锁，提交前读这张表的页面请求都会被卡住；`DELETE` 下读者一直看到旧快照。
@Component
@RequiredArgsConstructor
public class HotReadingSnapshotRepositoryAdapter implements HotReadingSnapshotRepository {

  private final JdbcClient jdbc;

  /// 整表替换并记下计算时间。
  ///
  /// @param entries 新快照的全部行
  /// @param computedAt 计算时间
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void replace(List<HotReadingEntry> entries, Instant computedAt) {
    jdbc.sql("DELETE FROM insight.hot_reading").update();
    for (int from = 0; from < entries.size(); from += SnapshotRuns.CHUNK) {
      insert(entries.subList(from, Math.min(from + SnapshotRuns.CHUNK, entries.size())));
    }
    SnapshotRuns.record(jdbc, SnapshotRuns.HOT_READING, computedAt, entries.size());
  }

  /// 一条 `INSERT ... SELECT FROM unnest(...)` 写一块。
  ///
  /// @param chunk 一块
  private void insert(List<HotReadingEntry> chunk) {
    jdbc.sql(
            "INSERT INTO insight.hot_reading (position, article_id, ranked, score, readers_7d)"
                + " SELECT * FROM unnest(CAST(:positions AS smallint[]), CAST(:articles AS bigint[]),"
                + " CAST(:ranked AS boolean[]), CAST(:scores AS double precision[]),"
                + " CAST(:readers AS integer[]))")
        .param("positions", chunk.stream().map(HotReadingEntry::position).toArray(Integer[]::new))
        .param("articles", chunk.stream().map(HotReadingEntry::articleId).toArray(Long[]::new))
        .param("ranked", chunk.stream().map(HotReadingEntry::ranked).toArray(Boolean[]::new))
        .param("scores", chunk.stream().map(HotReadingEntry::score).toArray(Double[]::new))
        .param("readers", chunk.stream().map(HotReadingEntry::readers7d).toArray(Integer[]::new))
        .update();
  }
}
