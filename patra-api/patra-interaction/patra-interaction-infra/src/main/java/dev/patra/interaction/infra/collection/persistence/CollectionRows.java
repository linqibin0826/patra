package dev.patra.interaction.infra.collection.persistence;

import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

/// 收藏子域几张表的行映射与参数转换，几个适配器共用。
final class CollectionRows {

  /// 读回收藏夹要的列：「更新于」取内容变更时间 `content_changed_at`，不取触发器维护的 `updated_at`。
  static final String COLLECTION_COLUMNS =
      "id, user_id, name, description, visibility, item_count, created_at, content_changed_at";

  /// 收藏夹行 → 摘要。
  static final RowMapper<CollectionSummary> COLLECTION =
      (rs, rowNum) ->
          CollectionSummary.builder()
              .collectionId(CollectionId.of(rs.getLong("id")))
              .ownerId(rs.getLong("user_id"))
              .name(rs.getString("name"))
              .description(rs.getString("description"))
              .visibility(CollectionVisibility.valueOf(rs.getString("visibility")))
              .itemCount(rs.getInt("item_count"))
              .createdAt(instant(rs, "created_at"))
              .updatedAt(instant(rs, "content_changed_at"))
              .build();

  /// 只有静态成员。
  private CollectionRows() {}

  /// 读一个 `timestamptz` 列。
  ///
  /// @param rs 结果集
  /// @param column 列名
  /// @return 时刻，列为空时 `null`
  /// @throws SQLException 读列失败
  static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  /// 时间参数（UTC）。
  ///
  /// @param instant 时刻
  /// @return 带偏移的时间
  static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  /// 对象 ID 参数。
  ///
  /// @param subjectIds 对象
  /// @return ID 值
  static List<Long> ids(Collection<SubjectId> subjectIds) {
    return subjectIds.stream().map(SubjectId::value).toList();
  }

  /// 对象 ID 数组参数（`unnest … WITH ORDINALITY` 保序用）。
  ///
  /// @param subjectIds 对象，按顺序
  /// @return ID 数组
  static Long[] idArray(List<SubjectId> subjectIds) {
    return subjectIds.stream().map(SubjectId::value).toArray(Long[]::new);
  }

  /// 一批新行的雪花 ID，给 `INSERT … SELECT … unnest` 与对象 ID 数组按位置配对。
  ///
  /// @param count 行数
  /// @return 雪花 ID
  static Long[] newRowIds(int count) {
    Long[] ids = new Long[count];
    for (int i = 0; i < count; i++) {
      ids[i] = SnowflakeIdGenerator.getId();
    }
    return ids;
  }
}
