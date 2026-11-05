package dev.patra.analytics.infra.insight.persistence;

import dev.patra.analytics.domain.insight.model.vo.CoReadingEntry;
import dev.patra.analytics.domain.insight.port.repository.CoReadingSnapshotRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `CoReadingSnapshotRepository` 的原生 SQL 实现：`DELETE` 全表 + 每 5,000 行一条 `unnest` 插入，要求调用方的事务。
@Component
@RequiredArgsConstructor
public class CoReadingSnapshotRepositoryAdapter implements CoReadingSnapshotRepository {

  private final JdbcClient jdbc;

  /// 整表替换并记下计算时间。
  ///
  /// @param entries 新快照的全部行
  /// @param computedAt 计算时间
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void replace(List<CoReadingEntry> entries, Instant computedAt) {
    jdbc.sql("DELETE FROM insight.co_reading").update();
    for (int from = 0; from < entries.size(); from += SnapshotRuns.CHUNK) {
      insert(entries.subList(from, Math.min(from + SnapshotRuns.CHUNK, entries.size())));
    }
    SnapshotRuns.record(jdbc, SnapshotRuns.CO_READING, computedAt, entries.size());
  }

  /// 一条 `INSERT ... SELECT FROM unnest(...)` 写一块。
  ///
  /// @param chunk 一块
  private void insert(List<CoReadingEntry> chunk) {
    jdbc.sql(
            "INSERT INTO insight.co_reading"
                + " (article_id, position, related_article_id, similarity, co_readers)"
                + " SELECT * FROM unnest(CAST(:articles AS bigint[]), CAST(:positions AS smallint[]),"
                + " CAST(:related AS bigint[]), CAST(:similarities AS double precision[]),"
                + " CAST(:coReaders AS integer[]))")
        .param("articles", chunk.stream().map(CoReadingEntry::articleId).toArray(Long[]::new))
        .param("positions", chunk.stream().map(CoReadingEntry::position).toArray(Integer[]::new))
        .param("related", chunk.stream().map(CoReadingEntry::relatedArticleId).toArray(Long[]::new))
        .param(
            "similarities", chunk.stream().map(CoReadingEntry::similarity).toArray(Double[]::new))
        .param("coReaders", chunk.stream().map(CoReadingEntry::coReaders).toArray(Integer[]::new))
        .update();
  }
}
