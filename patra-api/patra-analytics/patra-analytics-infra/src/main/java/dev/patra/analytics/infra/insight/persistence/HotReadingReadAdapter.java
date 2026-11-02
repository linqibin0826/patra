package dev.patra.analytics.infra.insight.persistence;

import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;
import dev.patra.analytics.domain.insight.model.vo.HotReadingSummary;
import dev.patra.analytics.domain.insight.port.read.HotReadingReadPort;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `HotReadingReadPort` 的原生 SQL 实现。
@Component
@RequiredArgsConstructor
public class HotReadingReadAdapter implements HotReadingReadPort {

  private final JdbcClient jdbc;

  /// 一条语句读出计算时间、榜上总数与前几行：替换恰好在两次读之间提交时，三者仍来自同一份快照。
  ///
  /// @param limit 最多几行
  /// @return 热读页
  @Override
  public HotReadingSummary summary(int limit) {
    List<Instant> computedAt = new ArrayList<>(1);
    List<Integer> rankedTotal = new ArrayList<>(1);
    List<HotReadingEntry> items = new ArrayList<>();
    jdbc.sql(
            "SELECT r.computed_at,"
                + " (SELECT count(*) FROM insight.hot_reading WHERE ranked) AS ranked_total,"
                + " h.position, h.article_id, h.ranked, h.score, h.readers_7d"
                + " FROM insight.snapshot_run r"
                + " LEFT JOIN LATERAL (SELECT * FROM insight.hot_reading"
                + " ORDER BY position LIMIT :limit) h ON true"
                + " WHERE r.kind = :kind ORDER BY h.position")
        .param("limit", limit)
        .param("kind", SnapshotRuns.HOT_READING)
        .query(
            rs -> {
              if (computedAt.isEmpty()) {
                computedAt.add(rs.getObject("computed_at", OffsetDateTime.class).toInstant());
                rankedTotal.add(rs.getInt("ranked_total"));
              }
              if (rs.getObject("position") != null) {
                items.add(
                    HotReadingEntry.builder()
                        .position(rs.getInt("position"))
                        .articleId(rs.getLong("article_id"))
                        .ranked(rs.getBoolean("ranked"))
                        .score(rs.getObject("score", Double.class))
                        .readers7d(rs.getObject("readers_7d", Integer.class))
                        .build());
              }
            });
    if (computedAt.isEmpty()) {
      return HotReadingSummary.empty();
    }
    return HotReadingSummary.of(computedAt.getFirst(), rankedTotal.getFirst(), items);
  }

  /// 按 `article_id` 的唯一索引查。
  ///
  /// @param ids 文献 ID
  /// @return 在榜上的
  @Override
  public Set<Long> rankedAmong(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql(
                "SELECT article_id FROM insight.hot_reading"
                    + " WHERE ranked AND article_id = ANY(CAST(:ids AS bigint[]))")
            .param("ids", ids.toArray(Long[]::new))
            .query(Long.class)
            .list());
  }
}
