package dev.patra.interaction.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// post 子域的迁移（PAP-122）：三张表、CHECK 兜底、外键、`updated_at` 触发器。约束测试断言**违反的约束名**，
/// 免得别的约束碰巧拦住、测试假绿。
@SpringBootTest(classes = InteractionITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("post 子域的迁移")
class PostMigrationIT {

  private static final Timestamp T0 = Timestamp.from(Instant.parse("2026-10-27T02:00:00Z"));

  @Autowired private JdbcTemplate jdbcTemplate;

  /// 每例从空表开始。
  @BeforeEach
  void truncate() {
    jdbcTemplate.update("TRUNCATE post.revision, post.submission_experience, post.post");
  }

  /// 三张表都在 post schema 里。
  @Test
  void should_create_post_tables() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'post' ORDER BY tablename",
                String.class))
        .containsExactly("post", "revision", "submission_experience");
  }

  /// 基表：类型、状态、「已删除 ⇔ 有删除时刻」。
  @Test
  void should_enforce_post_checks() {
    assertViolates("ck_post_type", () -> insertPost(1L, "HELP", "ACTIVE", null));
    assertViolates("ck_post_status", () -> insertPost(2L, "SUBMISSION_EXPERIENCE", "GONE", null));
    assertViolates(
        "ck_post_deleted", () -> insertPost(3L, "SUBMISSION_EXPERIENCE", "DELETED", null));
    assertViolates("ck_post_deleted", () -> insertPost(4L, "SUBMISSION_EXPERIENCE", "ACTIVE", T0));
    insertPost(5L, "SUBMISSION_EXPERIENCE", "DELETED", T0);

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM post.post", Long.class))
        .isEqualTo(1L);
  }

  /// 扩展表：三个枚举、轮数与评分范围、日期先后、进行中不填最终日期、外键。
  @Test
  void should_enforce_experience_checks() {
    insertPost(1L, "SUBMISSION_EXPERIENCE", "ACTIVE", null);

    assertViolates(
        "ck_experience_first_decision",
        () -> insertExperience(1L, Map.of("first_decision", "MAYBE")));
    assertViolates(
        "ck_experience_final_outcome", () -> insertExperience(1L, Map.of("final_outcome", "LOST")));
    assertViolates(
        "ck_experience_article_type", () -> insertExperience(1L, Map.of("article_type", "POEM")));
    assertViolates(
        "ck_experience_review_rounds", () -> insertExperience(1L, Map.of("review_rounds", 11)));
    assertViolates(
        "ck_experience_review_rounds", () -> insertExperience(1L, Map.of("review_rounds", 0)));
    assertViolates(
        "ck_experience_review_quality", () -> insertExperience(1L, Map.of("review_quality", 6)));
    assertViolates(
        "ck_experience_editor_communication",
        () -> insertExperience(1L, Map.of("editor_communication", 0)));
    assertViolates(
        "ck_experience_first_after_submission",
        () -> insertExperience(1L, Map.of("first_decision_on", "2026-03-01")));
    assertViolates(
        "ck_experience_final_after_first",
        () -> insertExperience(1L, Map.of("final_decision_on", "2026-04-12")));
    assertViolates(
        "ck_experience_in_progress_open",
        () ->
            insertExperience(
                1L, Map.of("final_outcome", "IN_PROGRESS", "final_decision_on", "2026-06-01")));
    assertViolates("fk_experience_post", () -> insertExperience(2L, Map.of()));
    insertExperience(1L, Map.of());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM post.submission_experience", Long.class))
        .isEqualTo(1L);
  }

  /// 修改历史：版本号从 1 起，外键到基表。
  @Test
  void should_enforce_revision_checks() {
    insertPost(1L, "SUBMISSION_EXPERIENCE", "ACTIVE", null);

    assertViolates("ck_revision_no", () -> insertRevision(1L, 0));
    assertViolates("fk_revision_post", () -> insertRevision(2L, 1));
    insertRevision(1L, 1);
  }

  /// 改任何列都推进 `updated_at`。
  @Test
  void should_touch_updated_at_on_update() {
    insertPost(1L, "SUBMISSION_EXPERIENCE", "ACTIVE", null);
    jdbcTemplate.update("UPDATE post.post SET updated_at = ? WHERE post_id = 1", T0);

    jdbcTemplate.update("UPDATE post.post SET body = '改了' WHERE post_id = 1");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT updated_at > ? FROM post.post WHERE post_id = 1", Boolean.class, T0))
        .isTrue();
  }

  /// 违反的正是这条约束。
  ///
  /// @param constraint 约束名
  /// @param insert 插入
  private static void assertViolates(String constraint, Runnable insert) {
    assertThatThrownBy(insert::run)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  /// 插一行基表（期刊 2、作者 9、非匿名）。
  ///
  /// @param id 帖子 ID
  /// @param type 类型
  /// @param status 状态
  /// @param deletedAt 删除时刻，可空
  private void insertPost(long id, String type, String status, Timestamp deletedAt) {
    jdbcTemplate.update(
        "INSERT INTO post.post (post_id, post_type, subject_id, author_user_id, anonymous, body,"
            + " status, created_at, deleted_at) VALUES (?, ?, 2, 9, false, NULL, ?, ?, ?)",
        id,
        type,
        status,
        T0,
        deletedAt);
  }

  /// 插一行扩展表：合法的默认值（3 月 2 日投稿、4 月 13 日大修、录用、原创研究）被 `overrides` 覆盖。
  ///
  /// @param postId 帖子 ID
  /// @param overrides 要覆盖的列
  private void insertExperience(long postId, Map<String, Object> overrides) {
    Map<String, Object> columns = new LinkedHashMap<>();
    columns.put("post_id", postId);
    columns.put("submitted_on", "2026-03-02");
    columns.put("first_decision_on", "2026-04-13");
    columns.put("first_decision", "MAJOR_REVISION");
    columns.put("final_outcome", "ACCEPTED");
    columns.put("article_type", "ORIGINAL_RESEARCH");
    columns.putAll(overrides);
    String names = String.join(", ", columns.keySet());
    String values =
        columns.keySet().stream()
            .map(c -> c.endsWith("_on") ? "CAST(? AS date)" : "?")
            .collect(Collectors.joining(", "));
    jdbcTemplate.update(
        "INSERT INTO post.submission_experience (" + names + ") VALUES (" + values + ")",
        columns.values().toArray());
  }

  /// 插一版修改历史。
  ///
  /// @param postId 帖子 ID
  /// @param revisionNo 版本号
  private void insertRevision(long postId, int revisionNo) {
    jdbcTemplate.update(
        "INSERT INTO post.revision (post_id, revision_no, body, fields, written_at, replaced_at)"
            + " VALUES (?, ?, NULL, CAST('{}' AS jsonb), ?, ?)",
        postId,
        revisionNo,
        T0,
        T0);
  }
}
