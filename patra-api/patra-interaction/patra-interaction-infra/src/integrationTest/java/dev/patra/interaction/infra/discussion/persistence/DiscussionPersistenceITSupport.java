package dev.patra.interaction.infra.discussion.persistence;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// discussion 持久化集成测试的公共底座：真实 PG，每例前清空讨论的表与关联读的 reaction 表；
/// 写操作包在事务里，模拟 app 层。
@SpringBootTest(classes = DiscussionPersistenceITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class DiscussionPersistenceITSupport {

  /// 对象。
  protected static final SubjectId S1 = SubjectId.of(11L);

  /// 对象。
  protected static final SubjectId S2 = SubjectId.of(12L);

  /// 时刻。
  protected static final Instant T0 = Instant.parse("2026-10-27T01:00:00Z");

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected TransactionTemplate transactions;

  /// 每例从空表开始。
  @BeforeEach
  void truncateDiscussionTables() {
    jdbcTemplate.update(
        "TRUNCATE discussion.comment_revision, discussion.comment, discussion.thread_stats,"
            + " discussion.subject_stats, reaction.target_stats, reaction.helpful");
  }

  /// 不经仓储直接插一行（约束测试用）。
  ///
  /// @param commentId 评论 ID
  /// @param rootId 根评论
  /// @param parentId 父评论，可空
  /// @param state 状态
  /// @param body 正文，可空
  /// @param deletedAt 删除时刻，可空
  protected void insertRaw(
      long commentId, long rootId, Long parentId, String state, String body, Instant deletedAt) {
    jdbcTemplate.update(
        "INSERT INTO discussion.comment (comment_id, subject_id, root_id, parent_id, author_id,"
            + " state, body, created_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        commentId,
        S1.value(),
        rootId,
        parentId,
        1L,
        state,
        body,
        Timestamp.from(T0),
        deletedAt == null ? null : Timestamp.from(deletedAt));
  }
}
