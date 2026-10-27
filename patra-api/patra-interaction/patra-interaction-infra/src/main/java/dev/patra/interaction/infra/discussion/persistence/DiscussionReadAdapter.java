package dev.patra.interaction.infra.discussion.persistence;

import dev.patra.interaction.domain.discussion.model.enums.ThreadSort;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.ThreadCursor;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `DiscussionReadPort` 的原生 SQL 实现。
@Component
@RequiredArgsConstructor
public class DiscussionReadAdapter implements DiscussionReadPort {

  private final JdbcClient jdbc;

  /// 根评论的一页。
  ///
  /// @param subjectId 对象
  /// @param sort 排序
  /// @param after 游标，可空
  /// @param limit 最多几条
  /// @return 根评论
  @Override
  public List<CommentRecord> findRoots(
      SubjectId subjectId, ThreadSort sort, ThreadCursor after, int limit) {
    boolean asc = sort == ThreadSort.ASC;
    String cursor =
        after == null
            ? ""
            : " AND (c.created_at, c.comment_id) " + (asc ? ">" : "<") + " (:at, :id)";
    String order =
        asc
            ? " ORDER BY c.created_at, c.comment_id"
            : " ORDER BY c.created_at DESC, c.comment_id DESC";
    JdbcClient.StatementSpec spec =
        jdbc.sql(
                CommentRows.RECORD_SELECT
                    + " WHERE c.subject_id = :subjectId AND c.parent_id IS NULL"
                    + cursor
                    + order
                    + " LIMIT :limit")
            .param("subjectId", subjectId.value())
            .param("limit", limit);
    return withCursor(spec, after).query(CommentRows.RECORD).list();
  }

  /// 每楼最早的若干条回复。
  ///
  /// @param rootIds 根评论
  /// @param perThread 每楼最多几条
  /// @return 根评论 → 回复
  @Override
  public Map<Long, List<CommentRecord>> findReplyPreviews(Collection<Long> rootIds, int perThread) {
    if (rootIds.isEmpty()) {
      return Map.of();
    }
    List<CommentRecord> rows =
        jdbc.sql(
                """
                WITH ranked AS (
                  SELECT comment_id,
                         row_number() OVER (PARTITION BY root_id ORDER BY created_at, comment_id)
                             AS rn
                    FROM discussion.comment
                   WHERE root_id IN (:rootIds) AND parent_id IS NOT NULL)
                """
                    + CommentRows.RECORD_SELECT
                    + " JOIN ranked k ON k.comment_id = c.comment_id WHERE k.rn <= :perThread"
                    + " ORDER BY c.root_id, c.created_at, c.comment_id")
            .param("rootIds", List.copyOf(rootIds))
            .param("perThread", perThread)
            .query(CommentRows.RECORD)
            .list();
    return rows.stream()
        .collect(
            Collectors.groupingBy(
                CommentRecord::rootId, LinkedHashMap::new, Collectors.toUnmodifiableList()));
  }

  /// 楼内回复的一页。
  ///
  /// @param rootId 根评论
  /// @param after 游标，可空
  /// @param limit 最多几条
  /// @return 回复
  @Override
  public List<CommentRecord> findReplies(long rootId, ThreadCursor after, int limit) {
    String cursor = after == null ? "" : " AND (c.created_at, c.comment_id) > (:at, :id)";
    JdbcClient.StatementSpec spec =
        jdbc.sql(
                CommentRows.RECORD_SELECT
                    + " WHERE c.root_id = :rootId AND c.parent_id IS NOT NULL"
                    + cursor
                    + " ORDER BY c.created_at, c.comment_id LIMIT :limit")
            .param("rootId", rootId)
            .param("limit", limit);
    return withCursor(spec, after).query(CommentRows.RECORD).list();
  }

  /// 整楼。
  ///
  /// @param rootId 根评论
  /// @return 根在前，回复正序
  @Override
  public List<CommentRecord> findThread(long rootId) {
    return jdbc.sql(
            CommentRows.RECORD_SELECT
                + " WHERE c.root_id = :rootId"
                + " ORDER BY c.parent_id IS NOT NULL, c.created_at, c.comment_id")
        .param("rootId", rootId)
        .query(CommentRows.RECORD)
        .list();
  }

  /// 按 ID 取一条。
  ///
  /// @param commentId 评论
  /// @return 评论
  @Override
  public Optional<CommentRecord> findById(long commentId) {
    return jdbc.sql(CommentRows.RECORD_SELECT + " WHERE c.comment_id = :id")
        .param("id", commentId)
        .query(CommentRows.RECORD)
        .optional();
  }

  /// 祖先链。
  ///
  /// @param commentId 评论
  /// @return 根在前
  @Override
  public List<CommentRecord> findAncestry(long commentId) {
    return jdbc.sql(
            """
            WITH RECURSIVE chain (comment_id, parent_id, depth) AS (
              SELECT comment_id, parent_id, 0 FROM discussion.comment WHERE comment_id = :id
              UNION ALL
              SELECT a.comment_id, a.parent_id, chain.depth + 1
                FROM discussion.comment a JOIN chain ON a.comment_id = chain.parent_id)
            """
                + CommentRows.RECORD_SELECT
                + " JOIN chain h ON h.comment_id = c.comment_id ORDER BY h.depth DESC")
        .param("id", commentId)
        .query(CommentRows.RECORD)
        .list();
  }

  /// 修改历史。
  ///
  /// @param commentId 评论
  /// @return 最新的在前
  @Override
  public List<RevisionRecord> findRevisions(long commentId) {
    return jdbc.sql(
            "SELECT body, version_at FROM discussion.comment_revision WHERE comment_id = :id"
                + " ORDER BY revision_no DESC")
        .param("id", commentId)
        .query(
            (rs, rowNum) ->
                RevisionRecord.of(rs.getString("body"), CommentRows.instant(rs, "version_at")))
        .list();
  }

  /// 最近发言的人。
  ///
  /// @param subjectId 对象
  /// @param excludedUserIds 排除
  /// @param limit 最多几位
  /// @return 用户 ID
  @Override
  public List<Long> findRecentParticipants(
      SubjectId subjectId, Collection<Long> excludedUserIds, int limit) {
    return jdbc.sql(
            """
            SELECT author_id FROM discussion.comment
             WHERE subject_id = :subjectId AND state = 'ACTIVE'
               AND author_id <> ALL (CAST(:excluded AS bigint[]))
             GROUP BY author_id
             ORDER BY max(created_at) DESC, author_id DESC
             LIMIT :limit
            """)
        .param("subjectId", subjectId.value())
        .param("excluded", CommentRows.idArray(excludedUserIds))
        .param("limit", limit)
        .query(Long.class)
        .list();
  }

  /// 有游标时补上游标的两个参数。
  ///
  /// @param spec 语句
  /// @param after 游标，可空
  /// @return 语句
  private static JdbcClient.StatementSpec withCursor(
      JdbcClient.StatementSpec spec, ThreadCursor after) {
    return after == null
        ? spec
        : spec.param("at", CommentRows.utc(after.createdAt())).param("id", after.commentId());
  }
}
