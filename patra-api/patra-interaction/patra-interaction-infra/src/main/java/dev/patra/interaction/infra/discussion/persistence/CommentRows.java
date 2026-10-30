package dev.patra.interaction.infra.discussion.persistence;

import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;

/// discussion 两张表的行映射与参数转换，两个适配器共用。
final class CommentRows {

  /// 读回聚合要的列。
  static final String AGGREGATE_COLUMNS =
      "comment_id, subject_id, root_id, parent_id, author_id, state, body, mentioned_user_ids,"
          + " created_at, revised_at, version";

  /// 评论行 → 聚合。
  static final RowMapper<CommentAggregate> AGGREGATE =
      (rs, rowNum) ->
          CommentAggregate.builder()
              .commentId(rs.getLong("comment_id"))
              .subjectId(SubjectId.of(rs.getLong("subject_id")))
              .rootId(rs.getLong("root_id"))
              .parentId(nullableLong(rs, "parent_id"))
              .authorId(rs.getLong("author_id"))
              .state(CommentState.valueOf(rs.getString("state")))
              .body(rs.getString("body"))
              .mentionedUserIds(longSet(rs, "mentioned_user_ids"))
              .createdAt(instant(rs, "created_at"))
              .revisedAt(instant(rs, "revised_at"))
              .version(rs.getInt("version"))
              .build();

  /// 读回带父评论信息的评论要的 `SELECT … FROM … LEFT JOIN`（表别名 `c` 是评论，`p` 是父评论，`hs` 是 reaction 的
  /// 有帮助统计），后面接条件。
  static final String RECORD_SELECT =
      """
      SELECT c.comment_id, c.subject_id, c.root_id, c.parent_id, c.author_id, c.state, c.body,
             c.created_at, c.revised_at, p.author_id AS parent_author_id, p.state AS parent_state,
             EXISTS (SELECT 1 FROM discussion.comment r WHERE r.parent_id = c.comment_id)
                 AS has_replies,
             COALESCE(hs.helpful_count, 0) AS helpful_count
        FROM discussion.comment c
        LEFT JOIN discussion.comment p ON p.comment_id = c.parent_id
        LEFT JOIN reaction.target_stats hs
               ON hs.target_type = 'COMMENT' AND hs.target_id = c.comment_id
      """;

  /// 评论行（含父评论信息）→ 读模型。
  static final RowMapper<CommentRecord> RECORD =
      (rs, rowNum) -> {
        String parentState = rs.getString("parent_state");
        return CommentRecord.builder()
            .commentId(rs.getLong("comment_id"))
            .subjectId(SubjectId.of(rs.getLong("subject_id")))
            .rootId(rs.getLong("root_id"))
            .parentId(nullableLong(rs, "parent_id"))
            .authorId(rs.getLong("author_id"))
            .state(CommentState.valueOf(rs.getString("state")))
            .body(rs.getString("body"))
            .createdAt(instant(rs, "created_at"))
            .revisedAt(instant(rs, "revised_at"))
            .parentAuthorId(nullableLong(rs, "parent_author_id"))
            .parentState(parentState == null ? null : CommentState.valueOf(parentState))
            .hasReplies(rs.getBoolean("has_replies"))
            .helpfulCount(rs.getInt("helpful_count"))
            .build();
      };

  /// 只有静态成员。
  private CommentRows() {}

  /// 读一个可空的 `BIGINT` 列。
  ///
  /// @param rs 结果集
  /// @param column 列名
  /// @return 值，列为空时 `null`
  /// @throws SQLException 读列失败
  static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

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

  /// 读一个 `BIGINT[]` 列。
  ///
  /// @param rs 结果集
  /// @param column 列名
  /// @return 集合
  /// @throws SQLException 读列失败
  static Set<Long> longSet(ResultSet rs, String column) throws SQLException {
    Array array = rs.getArray(column);
    return array == null ? Set.of() : Set.of((Long[]) array.getArray());
  }

  /// 时间参数（UTC）。
  ///
  /// @param instant 时刻，可空
  /// @return 带偏移的时间
  static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  /// 用户 ID 数组参数（SQL 里写 `CAST(:x AS bigint[])`）。
  ///
  /// @param ids 用户 ID
  /// @return 数组
  static Long[] idArray(Collection<Long> ids) {
    return ids.toArray(Long[]::new);
  }
}
