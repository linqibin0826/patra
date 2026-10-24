package dev.patra.interaction.infra.subject.persistence;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectKeyException;
import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `SubjectReadPort` 的原生 SQL 实现。
@Component
@RequiredArgsConstructor
public class SubjectReadAdapter implements SubjectReadPort {

  private final JdbcClient jdbc;
  private final SubjectRows rows;

  /// 按对外 key 查已登记对象；不合法的 key 跳过。
  ///
  /// @param type 对象种类
  /// @param keys 对外 key
  /// @return key → 本地 ID
  @Override
  public Map<String, SubjectId> findIdsByKeys(SubjectType type, Collection<String> keys) {
    requireAtMost(keys.size(), MAX_KEYS, "key");
    List<Long> sourceIds = new ArrayList<>();
    for (String key : keys) {
      try {
        sourceIds.add(type.parseKey(key));
      } catch (InvalidSubjectKeyException ex) {
        // 「我的状态」里不合法的 key 必然没登记过，跳过
      }
    }
    if (sourceIds.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql(
            "SELECT subject_id, source_id FROM subject.registry"
                + " WHERE target_type = :type AND source_id IN (:sourceIds)")
        .param("type", type.code())
        .param("sourceIds", sourceIds)
        .query(
            (rs, rowNum) ->
                Map.entry(
                    Long.toString(rs.getLong("source_id")), SubjectId.of(rs.getLong("subject_id"))))
        .list()
        .stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /// 按本地 ID 取视图。
  ///
  /// @param ids 本地 ID
  /// @return 本地 ID → 视图
  @Override
  public Map<SubjectId, SubjectView> findByIds(Collection<SubjectId> ids) {
    requireAtMost(ids.size(), MAX_IDS, "ID");
    if (ids.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql("SELECT " + SubjectRows.COLUMNS + " FROM subject.registry WHERE subject_id IN (:ids)")
        .param("ids", ids.stream().map(SubjectId::value).toList())
        .query(rows.aggregateMapper())
        .list()
        .stream()
        .collect(
            Collectors.toUnmodifiableMap(
                SubjectAggregate::getSubjectId, SubjectReadAdapter::toView));
  }

  /// 聚合 → 视图。
  ///
  /// @param subject 聚合
  /// @return 视图
  private static SubjectView toView(SubjectAggregate subject) {
    return SubjectView.builder()
        .subjectId(subject.getSubjectId())
        .type(subject.getType())
        .key(subject.getSourceId() == null ? null : Long.toString(subject.getSourceId()))
        .state(subject.getState())
        .snapshot(subject.getSnapshot())
        .build();
  }

  /// 批量上限。
  ///
  /// @param size 实际个数
  /// @param max 上限
  /// @param what 计量单位（消息用）
  private static void requireAtMost(int size, int max, String what) {
    if (size > max) {
      throw new IllegalArgumentException("一次最多查 " + max + " 个" + what + "：" + size);
    }
  }
}
