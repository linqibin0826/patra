package dev.patra.interaction.infra.post.persistence;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import dev.patra.interaction.domain.post.model.vo.PostBody;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `PostRepository` 的原生 SQL 实现。写方法不开事务，由 app 层的事务包住。
@Component
@RequiredArgsConstructor
public class PostRepositoryAdapter implements PostRepository {

  /// 作者锁的键前缀：`post.author:{userId}`。
  private static final String LOCK_PREFIX = "post.author:";

  private final JdbcClient jdbc;
  private final PostRows rows;

  /// 新 ID。
  ///
  /// @return 帖子 ID
  @Override
  public PostId nextId() {
    return PostId.of(SnowflakeIdGenerator.getId());
  }

  /// 作者锁。
  ///
  /// @param userId 作者
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockAuthor(long userId) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
        .param("key", LOCK_PREFIX + userId)
        .query((rs, rowNum) -> 1)
        .single();
  }

  /// 按 ID 取。
  ///
  /// @param id 帖子 ID
  /// @return 帖子
  @Override
  public Optional<PostAggregate> find(PostId id) {
    return jdbc.sql("SELECT " + PostRows.COLUMNS + PostRows.FROM + " WHERE p.post_id = :id")
        .param("id", id.value())
        .query(rows.postMapper())
        .optional();
  }

  /// 同一作者同一期刊同一投稿日期的未删除经验。
  ///
  /// @param authorId 作者
  /// @param journal 期刊
  /// @param submittedOn 投稿日期
  /// @return 已有经验的 ID
  @Override
  public Optional<PostId> findActiveBySubmission(
      long authorId, SubjectId journal, LocalDate submittedOn) {
    return jdbc.sql(
            "SELECT p.post_id"
                + PostRows.FROM
                + " WHERE p.author_user_id = :author AND p.subject_id = :journal"
                + " AND p.status = 'ACTIVE' AND e.submitted_on = :submittedOn"
                + " ORDER BY p.post_id LIMIT 1")
        .param("author", authorId)
        .param("journal", journal.value())
        .param("submittedOn", submittedOn)
        .query((rs, rowNum) -> PostId.of(rs.getLong("post_id")))
        .optional();
  }

  /// 插入两张表。
  ///
  /// @param post 帖子
  @Override
  public void insert(PostAggregate post) {
    jdbc.sql(
            """
            INSERT INTO post.post (post_id, post_type, subject_id, author_user_id, anonymous, body,
                                   status, created_at, revised_at)
            VALUES (:id, :type, :subjectId, :author, :anonymous, :body, :status, :createdAt, :revisedAt)
            """)
        .param("id", post.postId().value())
        .param("type", post.type().name())
        .param("subjectId", post.subjectId().value())
        .param("author", post.authorId())
        .param("anonymous", post.anonymous())
        .param("body", bodyValue(post.body()))
        .param("status", post.status().name())
        .param("createdAt", PostRows.utc(post.createdAt()))
        .param("revisedAt", PostRows.utc(post.revisedAt()))
        .update();
    jdbc.sql(
            """
            INSERT INTO post.submission_experience
                   (post_id, submitted_on, first_decision_on, first_decision, final_outcome,
                    final_decision_on, article_type, review_rounds, review_quality,
                    editor_communication, apc_paid, previously_rejected)
            VALUES (:id, :submittedOn, :firstDecisionOn, :firstDecision, :finalOutcome,
                    :finalDecisionOn, :articleType, :reviewRounds, :reviewQuality,
                    :editorCommunication, :apcPaid, :previouslyRejected)
            """)
        .paramSource(fieldParams(post.postId(), post.fields()))
        .update();
  }

  /// 写回内容。
  ///
  /// @param post 编辑后的帖子
  /// @param now 编辑时刻
  @Override
  public void update(PostAggregate post, Instant now) {
    jdbc.sql(
            "UPDATE post.post SET body = :body, revised_at = :revisedAt, updated_at = :now"
                + " WHERE post_id = :id")
        .param("body", bodyValue(post.body()))
        .param("revisedAt", PostRows.utc(post.revisedAt()))
        .param("now", PostRows.utc(now))
        .param("id", post.postId().value())
        .update();
    jdbc.sql(
            """
            UPDATE post.submission_experience
               SET submitted_on = :submittedOn, first_decision_on = :firstDecisionOn,
                   first_decision = :firstDecision, final_outcome = :finalOutcome,
                   final_decision_on = :finalDecisionOn, article_type = :articleType,
                   review_rounds = :reviewRounds, review_quality = :reviewQuality,
                   editor_communication = :editorCommunication, apc_paid = :apcPaid,
                   previously_rejected = :previouslyRejected
             WHERE post_id = :id
            """)
        .paramSource(fieldParams(post.postId(), post.fields()))
        .update();
  }

  /// 记一版旧内容。
  ///
  /// @param id 帖子 ID
  /// @param revision 旧版本
  @Override
  public void insertRevision(PostId id, PostRevision revision) {
    jdbc.sql(
            """
            INSERT INTO post.revision (post_id, revision_no, body, fields, written_at, replaced_at)
            SELECT :id, COALESCE(MAX(revision_no), 0) + 1, :body, CAST(:fields AS jsonb),
                   :writtenAt, :replacedAt
              FROM post.revision WHERE post_id = :id
            """)
        .param("id", id.value())
        .param("body", bodyValue(revision.body()))
        .param("fields", rows.writeFields(revision.fields()))
        .param("writtenAt", PostRows.utc(revision.writtenAt()))
        .param("replacedAt", PostRows.utc(revision.replacedAt()))
        .update();
  }

  /// 软删。
  ///
  /// @param id 帖子 ID
  /// @param now 删除时刻
  @Override
  public void markDeleted(PostId id, Instant now) {
    jdbc.sql(
            "UPDATE post.post SET status = 'DELETED', deleted_at = :now, updated_at = :now"
                + " WHERE post_id = :id AND status = 'ACTIVE'")
        .param("now", PostRows.utc(now))
        .param("id", id.value())
        .update();
  }

  /// 扩展表一行的参数（`paramSource` 会替换掉逐个 `param`，所以帖子 ID 也放在这里）。
  ///
  /// @param id 帖子 ID
  /// @param f 字段
  /// @return 参数
  private static MapSqlParameterSource fieldParams(PostId id, ExperienceFields f) {
    return new MapSqlParameterSource()
        .addValue("id", id.value())
        .addValue("submittedOn", f.submittedOn())
        .addValue("firstDecisionOn", f.firstDecisionOn())
        .addValue("firstDecision", f.firstDecision().name())
        .addValue("finalOutcome", f.finalOutcome().name())
        .addValue("finalDecisionOn", f.finalDecisionOn(), Types.DATE)
        .addValue("articleType", f.articleType().name())
        .addValue("reviewRounds", f.reviewRounds(), Types.SMALLINT)
        .addValue("reviewQuality", f.reviewQuality(), Types.SMALLINT)
        .addValue("editorCommunication", f.editorCommunication(), Types.SMALLINT)
        .addValue("apcPaid", f.apcPaid(), Types.BOOLEAN)
        .addValue("previouslyRejected", f.previouslyRejected(), Types.BOOLEAN);
  }

  /// 正文参数。
  ///
  /// @param body 正文，可空
  /// @return 文本，可空
  private static String bodyValue(PostBody body) {
    return body == null ? null : body.value();
  }
}
