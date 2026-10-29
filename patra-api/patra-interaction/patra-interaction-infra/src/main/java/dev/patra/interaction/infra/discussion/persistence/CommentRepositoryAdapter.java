package dev.patra.interaction.infra.discussion.persistence;

import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.NewComment;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
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

  /// 改正文。
  ///
  /// @param comment 改完的评论
  @Override
  public void updateBody(CommentAggregate comment) {
    jdbc.sql(
            """
            UPDATE discussion.comment
               SET body = :body, mentioned_user_ids = CAST(:mentioned AS bigint[]),
                   revised_at = :revisedAt
             WHERE comment_id = :id
            """)
        .param("body", comment.getBody())
        .param("mentioned", CommentRows.idArray(comment.getMentionedUserIds()))
        .param("revisedAt", CommentRows.utc(comment.getRevisedAt()))
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

  /// 按 ID 取一行。
  ///
  /// @param sql 查询
  /// @param commentId 评论
  /// @return 评论
  private Optional<CommentAggregate> findWith(String sql, long commentId) {
    return jdbc.sql(sql).param("id", commentId).query(CommentRows.AGGREGATE).optional();
  }
}
