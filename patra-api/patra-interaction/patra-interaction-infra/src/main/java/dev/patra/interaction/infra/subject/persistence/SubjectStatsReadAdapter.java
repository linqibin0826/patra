package dev.patra.interaction.infra.subject.persistence;

import dev.patra.interaction.domain.subject.model.read.SubjectStats;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectStatsReadPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `SubjectStatsReadPort` 的原生 SQL 实现：每张统计表一条按主键的语句，按对象合并。只读表，不 import 收藏、讨论的
/// 任何类。
@Component
@RequiredArgsConstructor
public class SubjectStatsReadAdapter implements SubjectStatsReadPort {

  private final JdbcClient jdbc;

  /// 读收藏人数与讨论数，按对象合并。
  ///
  /// @param ids 本地 ID
  /// @return 本地 ID → 计数
  @Override
  public Map<SubjectId, SubjectStats> findStats(Collection<SubjectId> ids) {
    if (ids.size() > MAX_IDS) {
      throw new IllegalArgumentException("一次最多查 " + MAX_IDS + " 个对象：" + ids.size());
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<Long> values = ids.stream().map(SubjectId::value).toList();
    Map<SubjectId, SubjectStats> result = new HashMap<>();
    jdbc.sql(
            "SELECT subject_id, collector_count FROM collection.subject_stats"
                + " WHERE subject_id IN (:ids)")
        .param("ids", values)
        .query(
            rs -> {
              result.put(
                  SubjectId.of(rs.getLong("subject_id")),
                  SubjectStats.of(rs.getInt("collector_count"), 0));
            });
    jdbc.sql(
            "SELECT subject_id, comment_count FROM discussion.subject_stats"
                + " WHERE subject_id IN (:ids)")
        .param("ids", values)
        .query(
            rs -> {
              int discussions = rs.getInt("comment_count");
              result.merge(
                  SubjectId.of(rs.getLong("subject_id")),
                  SubjectStats.of(0, discussions),
                  (collected, discussed) ->
                      SubjectStats.of(collected.collectorCount(), discussions));
            });
    return Map.copyOf(result);
  }
}
