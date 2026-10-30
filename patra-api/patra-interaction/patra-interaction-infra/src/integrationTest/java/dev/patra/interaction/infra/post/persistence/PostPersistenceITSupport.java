package dev.patra.interaction.infra.post.persistence;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.ArticleType;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.vo.ExperienceDraft;
import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import dev.patra.interaction.domain.post.model.vo.PostBody;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// post 持久化集成测试的公共底座：真实 PG，每例前清空三张表；写操作包在事务里，模拟 app 层。
@SpringBootTest(classes = PostPersistenceITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class PostPersistenceITSupport {

  /// 作者 U。
  protected static final long U = 1001L;

  /// 作者 V。
  protected static final long V = 1002L;

  /// 期刊 J1。
  protected static final SubjectId J1 = SubjectId.of(21L);

  /// 期刊 J2。
  protected static final SubjectId J2 = SubjectId.of(22L);

  /// 时刻。
  protected static final Instant T0 = Instant.parse("2026-10-27T01:00:00Z");

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected TransactionTemplate transactions;
  @Autowired protected PostRepository posts;

  /// 每例从空表开始。
  @BeforeEach
  void truncatePostTables() {
    jdbcTemplate.update(
        "TRUNCATE post.revision, post.submission_experience, post.post, reaction.target_stats,"
            + " reaction.helpful, discussion.subject_stats, subject.registry");
  }

  /// 只有必填项的字段：给定投稿日期，首轮同日起 10 天后大修，进行中，原创研究。
  ///
  /// @param submittedOn 投稿日期
  /// @return 字段
  protected static ExperienceFields fields(String submittedOn) {
    LocalDate submitted = LocalDate.parse(submittedOn);
    return ExperienceFields.builder()
        .submittedOn(submitted)
        .firstDecisionOn(submitted.plusDays(10))
        .firstDecision(FirstDecision.MAJOR_REVISION)
        .finalOutcome(FinalOutcome.IN_PROGRESS)
        .articleType(ArticleType.ORIGINAL_RESEARCH)
        .build();
  }

  /// 在事务里发布一条经验。
  ///
  /// @param author 作者
  /// @param journal 期刊
  /// @param anonymous 是否匿名
  /// @param fields 字段
  /// @param body 正文，可空
  /// @param at 发布时刻
  /// @return 帖子
  protected PostAggregate publish(
      long author,
      SubjectId journal,
      boolean anonymous,
      ExperienceFields fields,
      String body,
      Instant at) {
    PostAggregate post =
        PostAggregate.publish(
            posts.nextId(),
            journal,
            author,
            anonymous,
            ExperienceDraft.of(fields, body == null ? null : new PostBody(body)),
            at);
    transactions.executeWithoutResult(s -> posts.insert(post));
    return post;
  }

  /// 在事务里软删。
  ///
  /// @param id 帖子
  /// @param at 删除时刻
  protected void delete(PostId id, Instant at) {
    transactions.executeWithoutResult(s -> posts.markDeleted(id, at));
  }
}
