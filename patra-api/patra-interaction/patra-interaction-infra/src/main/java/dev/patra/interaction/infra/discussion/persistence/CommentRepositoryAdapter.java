package dev.patra.interaction.infra.discussion.persistence;

import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.NewComment;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `CommentRepository` 的原生 SQL 实现。写方法不开事务，由 app 层的事务包住。
@Component
@RequiredArgsConstructor
public class CommentRepositoryAdapter implements CommentRepository {

  /// 楼锁的键前缀：`discussion.thread:{rootId}`。
  private static final String THREAD_LOCK_PREFIX = "discussion.thread:";

  private static final String SELECT_COMMENT =
      "SELECT " + CommentRows.AGGREGATE_COLUMNS + " FROM discussion.comment WHERE comment_id = :id";

  private final JdbcClient jdbc;

  /// 拿楼锁。
  ///
  /// @param rootId 根评论
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockThread(long rootId) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
        .param("lockKey", THREAD_LOCK_PREFIX + rootId)
        .query((rs, rowNum) -> 1)
        .single();
  }

  /// 按 ID 取。
  ///
  /// @param commentId 评论
  /// @return 评论
  @Override
  public Optional<CommentAggregate> find(long commentId) {
    return findWith(SELECT_COMMENT, commentId);
  }

  /// 按 ID 取并锁行（删除用）。
  ///
  /// @param commentId 评论
  /// @return 评论
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<CommentAggregate> findForUpdate(long commentId) {
    return findWith(SELECT_COMMENT + " FOR UPDATE", commentId);
  }

  /// 按 ID 取并锁行（编辑用）。
  ///
  /// @param commentId 评论
  /// @return 评论
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<CommentAggregate> findForEdit(long commentId) {
    return findWith(SELECT_COMMENT + " FOR NO KEY UPDATE", commentId);
  }

  /// 有没有直接回复。
  ///
  /// @param commentId 评论
  /// @return 有时为 `true`
  @Override
  public boolean hasReplies(long commentId) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM discussion.comment WHERE parent_id = :id)")
        .param("id", commentId)
        .query(Boolean.class)
        .single();
  }

  /// 插入新评论。
  ///
  /// @param comment 新评论
  /// @return 新评论 ID
  @Override
  public long insert(NewComment comment) {
    long id = SnowflakeIdGenerator.getId();
    Long rootId = comment.rootId();
    if (rootId == null) {
      rootId = id;
    }
    jdbc.sql(
            """
            INSERT INTO discussion.comment
                   (comment_id, subject_id, root_id, parent_id, author_id, state, body,
                    mentioned_user_ids, created_at)
            VALUES (:id, :subjectId, :rootId, :parentId, :authorId, 'ACTIVE', :body,
                    CAST(:mentioned AS bigint[]), :createdAt)
            """)
        .param("id", id)
        .param("subjectId", comment.subjectId().value())
        .param("rootId", rootId)
        .param("parentId", comment.parentId())
        .param("authorId", comment.authorId())
        .param("body", comment.body())
        .param("mentioned", CommentRows.idArray(comment.mentionedUserIds()))
        .param("createdAt", CommentRows.utc(comment.createdAt()))
        .update();
    return id;
  }

  /// 改正文，连同加一后的正文版本。
  ///
  /// @param comment 改完的评论
  @Override
  public void updateBody(CommentAggregate comment) {
    jdbc.sql(
            """
            UPDATE discussion.comment
               SET body = :body, mentioned_user_ids = CAST(:mentioned AS bigint[]),
                   revised_at = :revisedAt, version = :version
             WHERE comment_id = :id
            """)
        .param("body", comment.getBody())
        .param("mentioned", CommentRows.idArray(comment.getMentionedUserIds()))
        .param("revisedAt", CommentRows.utc(comment.getRevisedAt()))
        .param("version", comment.getVersion())
        .param("id", comment.getCommentId())
        .update();
  }

  /// 追加一个旧版本。
  ///
  /// @param commentId 评论
  /// @param revision 旧版本
  @Override
  public void appendRevision(long commentId, RevisionRecord revision) {
    jdbc.sql(
            """
            INSERT INTO discussion.comment_revision (comment_id, revision_no, body, version_at)
            SELECT :id, COALESCE(max(revision_no), 0) + 1, :body, :at
              FROM discussion.comment_revision WHERE comment_id = :id
            """)
        .param("id", commentId)
        .param("body", revision.body())
        .param("at", CommentRows.utc(revision.at()))
        .update();
  }

  /// 变占位。
  ///
  /// @param commentId 评论
  /// @param now 删除时刻
  @Override
  public void markDeleted(long commentId, Instant now) {
    jdbc.sql(
            """
            UPDATE discussion.comment
               SET state = 'DELETED', body = NULL, mentioned_user_ids = '{}', deleted_at = :now
             WHERE comment_id = :id
            """)
        .param("now", CommentRows.utc(now))
        .param("id", commentId)
        .update();
    jdbc.sql("DELETE FROM discussion.comment_revision WHERE comment_id = :id")
        .param("id", commentId)
        .update();
  }

  /// 整条删除。
  ///
  /// @param commentId 评论
  @Override
  public void delete(long commentId) {
    jdbc.sql("DELETE FROM discussion.comment WHERE comment_id = :id")
        .param("id", commentId)
        .update();
  }

  /// 楼里多了一条回复。
  ///
  /// @param rootId 根评论
  /// @param createdAt 新回复的发表时刻
  @Override
  public void recordReply(long rootId, Instant createdAt) {
    jdbc.sql(
            """
            INSERT INTO discussion.thread_stats (root_id, reply_count, last_reply_at)
            VALUES (:rootId, 1, :at)
            ON CONFLICT (root_id) DO UPDATE
               SET reply_count = discussion.thread_stats.reply_count + 1,
                   last_reply_at = GREATEST(discussion.thread_stats.last_reply_at,
                                            EXCLUDED.last_reply_at)
            """)
        .param("rootId", rootId)
        .param("at", CommentRows.utc(createdAt))
        .update();
  }

  /// 楼里少了一条回复：锁行与重取最新时间分两条语句，后一条的快照晚于行锁。
  ///
  /// @param rootId 根评论
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void removeReply(long rootId) {
    jdbc.sql("SELECT 1 FROM discussion.thread_stats WHERE root_id = :rootId FOR UPDATE")
        .param("rootId", rootId)
        .query((rs, rowNum) -> 1)
        .list();
    jdbc.sql(
            """
            UPDATE discussion.thread_stats
               SET reply_count = reply_count - 1,
                   last_reply_at = (SELECT max(created_at) FROM discussion.comment
                                     WHERE root_id = :rootId AND parent_id IS NOT NULL
                                       AND state = 'ACTIVE')
             WHERE root_id = :rootId
            """)
        .param("rootId", rootId)
        .update();
  }

  /// 删楼统计行。
  ///
  /// @param rootId 根评论
  @Override
  public void dropThreadStats(long rootId) {
    jdbc.sql("DELETE FROM discussion.thread_stats WHERE root_id = :rootId")
        .param("rootId", rootId)
        .update();
  }

  /// 对象评论数 +1。
  ///
  /// @param subjectId 对象
  @Override
  public void incrementCommentCount(SubjectId subjectId) {
    jdbc.sql(
            """
            INSERT INTO discussion.subject_stats (subject_id, comment_count)
            VALUES (:subjectId, 1)
            ON CONFLICT (subject_id) DO UPDATE
               SET comment_count = discussion.subject_stats.comment_count + 1
            """)
        .param("subjectId", subjectId.value())
        .update();
  }

  /// 对象评论数 −1。
  ///
  /// @param subjectId 对象
  @Override
  public void decrementCommentCount(SubjectId subjectId) {
    jdbc.sql(
            "UPDATE discussion.subject_stats SET comment_count = comment_count - 1"
                + " WHERE subject_id = :subjectId")
        .param("subjectId", subjectId.value())
        .update();
  }

  /// 按 ID 取一行。
  ///
  /// @param sql 查询
  /// @param commentId 评论
  /// @return 评论
  private Optional<CommentAggregate> findWith(String sql, long commentId) {
    return jdbc.sql(sql).param("id", commentId).query(CommentRows.AGGREGATE).optional();
  }

  /// 回复按楼聚合（只数未删除的），与楼统计行全外连接；另外把根评论已不存在的统计行也算进来。
  ///
  /// @return 根评论 ID
  @Override
  public List<Long> findThreadStatsDrift() {
    return jdbc.sql(
            """
            SELECT COALESCE(d.root_id, s.root_id) AS root_id
              FROM (SELECT root_id,
                           count(*) FILTER (WHERE state = 'ACTIVE') AS n,
                           max(created_at) FILTER (WHERE state = 'ACTIVE') AS last_at
                      FROM discussion.comment
                     WHERE parent_id IS NOT NULL
                     GROUP BY root_id) d
              FULL JOIN discussion.thread_stats s ON s.root_id = d.root_id
             WHERE COALESCE(d.n, 0) <> COALESCE(s.reply_count, 0)
                OR d.last_at IS DISTINCT FROM s.last_reply_at
                OR (s.root_id IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM discussion.comment c WHERE c.comment_id = s.root_id))
            """)
        .query(Long.class)
        .list();
  }

  /// 楼锁内没有并发写：根评论不在就删行；否则按明细重取，不一致就 upsert。没有未删除回复时写 0 / 空（与回复全删光后
  /// 的写方一致，保留行）。
  ///
  /// @param rootId 根评论
  /// @return 改了才有值
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<StatsCorrection> recountThreadStats(long rootId) {
    Optional<ThreadCounts> current =
        jdbc.sql(
                "SELECT reply_count, last_reply_at FROM discussion.thread_stats"
                    + " WHERE root_id = :rootId")
            .param("rootId", rootId)
            .query(
                (rs, rowNum) ->
                    new ThreadCounts(
                        rs.getInt("reply_count"), CommentRows.instant(rs, "last_reply_at")))
            .optional();
    int before = current.map(ThreadCounts::replies).orElse(0);
    boolean rootExists =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM discussion.comment WHERE comment_id = :rootId)")
            .param("rootId", rootId)
            .query(Boolean.class)
            .single();
    if (!rootExists) {
      if (current.isEmpty()) {
        return Optional.empty();
      }
      dropThreadStats(rootId);
      return Optional.of(StatsCorrection.of(before, 0));
    }
    ThreadCounts truth =
        jdbc.sql(
                """
                SELECT count(*) AS n, max(created_at) AS last_at FROM discussion.comment
                 WHERE root_id = :rootId AND parent_id IS NOT NULL AND state = 'ACTIVE'
                """)
            .param("rootId", rootId)
            .query(
                (rs, rowNum) ->
                    new ThreadCounts(rs.getInt("n"), CommentRows.instant(rs, "last_at")))
            .single();
    if (truth.equals(current.orElse(ThreadCounts.NONE))) {
      return Optional.empty();
    }
    jdbc.sql(
            """
            INSERT INTO discussion.thread_stats (root_id, reply_count, last_reply_at)
            VALUES (:rootId, :n, :lastAt)
            ON CONFLICT (root_id) DO UPDATE
               SET reply_count = EXCLUDED.reply_count, last_reply_at = EXCLUDED.last_reply_at
            """)
        .param("rootId", rootId)
        .param("n", truth.replies())
        .param("lastAt", CommentRows.utc(truth.lastReplyAt()))
        .update();
    return Optional.of(StatsCorrection.of(before, truth.replies()));
  }

  /// 未删除的评论按对象聚合，与对象统计行全外连接，两边缺行都按 0 比较。
  ///
  /// @return 对象
  @Override
  public List<SubjectId> findCommentCountDrift() {
    return jdbc.sql(
            """
            SELECT COALESCE(d.subject_id, s.subject_id) AS subject_id
              FROM (SELECT subject_id, count(*) FILTER (WHERE state = 'ACTIVE') AS n
                      FROM discussion.comment GROUP BY subject_id) d
              FULL JOIN discussion.subject_stats s ON s.subject_id = d.subject_id
             WHERE COALESCE(d.n, 0) <> COALESCE(s.comment_count, 0)
            """)
        .query((rs, rowNum) -> SubjectId.of(rs.getLong("subject_id")))
        .list();
  }

  /// 先建出行、`FOR UPDATE` 锁住，再用新语句数：读已提交下新语句的快照晚于拿到锁，没提交的写入会在锁释放后在写下的
  /// 值上再 ±1，不会丢。
  ///
  /// @param subjectId 对象
  /// @return 改了才有值
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<StatsCorrection> recountCommentCount(SubjectId subjectId) {
    jdbc.sql(
            "INSERT INTO discussion.subject_stats (subject_id, comment_count) VALUES (:id, 0)"
                + " ON CONFLICT (subject_id) DO NOTHING")
        .param("id", subjectId.value())
        .update();
    int before =
        jdbc.sql(
                "SELECT comment_count FROM discussion.subject_stats WHERE subject_id = :id"
                    + " FOR UPDATE")
            .param("id", subjectId.value())
            .query(Integer.class)
            .single();
    int after =
        jdbc.sql(
                "SELECT count(*) FROM discussion.comment WHERE subject_id = :id AND state = 'ACTIVE'")
            .param("id", subjectId.value())
            .query(Integer.class)
            .single();
    if (before == after) {
      return Optional.empty();
    }
    jdbc.sql("UPDATE discussion.subject_stats SET comment_count = :n WHERE subject_id = :id")
        .param("n", after)
        .param("id", subjectId.value())
        .update();
    return Optional.of(StatsCorrection.of(before, after));
  }

  /// 一楼的统计值（重算时比对用）。
  ///
  /// @param replies 未删除的回复数
  /// @param lastReplyAt 最新一条未删除回复的发表时刻，没有为 `null`
  private record ThreadCounts(int replies, Instant lastReplyAt) {

    /// 没有统计行：0 条、没有时间。
    static final ThreadCounts NONE = new ThreadCounts(0, null);
  }
}
