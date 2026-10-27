package dev.patra.interaction.infra.subject.persistence;

import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/// `subject.registry` 的行映射与快照的 jsonb 编解码，两个适配器共用。
@Component
@RequiredArgsConstructor
class SubjectRows {

  /// 读回聚合要的列（`snapshot` 以文本取出）。
  static final String COLUMNS =
      "subject_id, target_type, natural_key, source_id, state, snapshot::text AS snapshot,"
          + " snapshot_at, owner_user_id, owner_anonymous";

  private final JsonMapper jsonMapper;

  /// 行 → 聚合。
  ///
  /// @return 行映射器
  RowMapper<SubjectAggregate> aggregateMapper() {
    return (rs, rowNum) -> toAggregate(rs);
  }

  /// 读一行。
  ///
  /// @param rs 结果集（当前行）
  /// @return 聚合
  /// @throws SQLException 读列失败
  SubjectAggregate toAggregate(ResultSet rs) throws SQLException {
    Long sourceId = rs.getObject("source_id", Long.class);
    return SubjectAggregate.builder()
        .subjectId(SubjectId.of(rs.getLong("subject_id")))
        .type(SubjectType.fromCode(rs.getShort("target_type")))
        .naturalKey(NaturalKey.parse(rs.getString("natural_key")))
        .sourceId(sourceId)
        .state(SubjectState.valueOf(rs.getString("state")))
        .snapshot(jsonMapper.readValue(rs.getString("snapshot"), SubjectSnapshot.class))
        .snapshotAt(rs.getObject("snapshot_at", OffsetDateTime.class).toInstant())
        .owner(owner(rs))
        .build();
  }

  /// 读所有者两列：外部对象两列都为空。
  ///
  /// @param rs 结果集（当前行）
  /// @return 所有者；外部对象为 `null`
  /// @throws SQLException 读列失败
  private static SubjectOwner owner(ResultSet rs) throws SQLException {
    Long ownerUserId = rs.getObject("owner_user_id", Long.class);
    return ownerUserId == null
        ? null
        : SubjectOwner.of(ownerUserId, rs.getBoolean("owner_anonymous"));
  }

  /// 快照 → jsonb 文本。
  ///
  /// @param snapshot 快照
  /// @return JSON
  String writeSnapshot(SubjectSnapshot snapshot) {
    return jsonMapper.writeValueAsString(snapshot);
  }

  /// 时间参数（UTC）。
  ///
  /// @param instant 时刻
  /// @return 带偏移的时间
  static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
