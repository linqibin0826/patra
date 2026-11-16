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

/// post 子域的迁移：三张表与 CHECK 兜底（没有外键，帖子与扩展、修订的对应由写入路径保证）。约束测试断言**违反的约束名**，
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
    jdbcTemplate.update("TRUNCATE itr_post_revision, itr_post_submission_experience, itr_post");
  }

  /// 三张表都在 public 里，以 `itr_post` 为前缀。
  @Test
  void should_create_post_tables() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
                    + " AND tablename LIKE 'itr\\_post%' ORDER BY tablename",
                String.class))
        .containsExactly("itr_post", "itr_post_revision", "itr_post_submission_experience");
  }

  /// 基表：类型、状态、「已删除 ⇔ 有删除时刻」。
  @Test
  void should_enforce_post_checks() {
    assertViolates("ck_itr_post_type", () -> insertPost(1L, "HELP", "ACTIVE", null));
    assertViolates(
        "ck_itr_post_status", () -> insertPost(2L, "SUBMISSION_EXPERIENCE", "GONE", null));
    assertViolates(
        "ck_itr_post_deleted", () -> insertPost(3L, "SUBMISSION_EXPERIENCE", "DELETED", null));
    assertViolates(
        "ck_itr_post_deleted", () -> insertPost(4L, "SUBMISSION_EXPERIENCE", "ACTIVE", T0));
    insertPost(5L, "SUBMISSION_EXPERIENCE", "DELETED", T0);

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM itr_post", Long.class))
        .isEqualTo(1L);
  }

  /// 扩展表：三个枚举、轮数与评分范围、日期先后、进行中不填最终日期。
  @Test
  void should_enforce_experience_checks() {
    insertPost(1L, "SUBMISSION_EXPERIENCE", "ACTIVE", null);

    assertViolates(
        "ck_itr_post_submission_experience_first",
        () -> insertExperience(1L, Map.of("first_decision", "MAYBE")));
    assertViolates(
        "ck_itr_post_submission_experience_final",
        () -> insertExperience(1L, Map.of("final_outcome", "LOST")));
    assertViolates(
        "ck_itr_post_submission_experience_article",
        () -> insertExperience(1L, Map.of("article_type", "POEM")));
    assertViolates(
        "ck_itr_post_submission_experience_rounds",
        () -> insertExperience(1L, Map.of("review_rounds", 11)));
    assertViolates(
        "ck_itr_post_submission_experience_rounds",
        () -> insertExperience(1L, Map.of("review_rounds", 0)));
    assertViolates(
        "ck_itr_post_submission_experience_quality",
        () -> insertExperience(1L, Map.of("review_quality", 6)));
    assertViolates(
        "ck_itr_post_submission_experience_communication",
        () -> insertExperience(1L, Map.of("editor_communication", 0)));
    assertViolates(
        "ck_itr_post_submission_experience_first_order",
        () -> insertExperience(1L, Map.of("first_decision_on", "2026-03-01")));
    assertViolates(
        "ck_itr_post_submission_experience_final_order",
        () -> insertExperience(1L, Map.of("final_decision_on", "2026-04-12")));
    assertViolates(
        "ck_itr_post_submission_experience_open",
        () ->
            insertExperience(
                1L, Map.of("final_outcome", "IN_PROGRESS", "final_decision_on", "2026-06-01")));
    insertExperience(1L, Map.of());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM itr_post_submission_experience", Long.class))
        .isEqualTo(1L);
  }

  /// 修改历史：版本号从 1 起。
  @Test
  void should_enforce_revision_checks() {
    insertPost(1L, "SUBMISSION_EXPERIENCE", "ACTIVE", null);

    assertViolates("ck_itr_post_revision_no", () -> insertRevision(1L, 0));
    insertRevision(1L, 1);
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
        "INSERT INTO itr_post (id, post_type, subject_id, author_user_id, anonymous, body,"
            + " status, published_at, deleted_at) VALUES (?, ?, 2, 9, false, NULL, ?, ?, ?)",
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
    columns.put("id", postId);
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
        "INSERT INTO itr_post_submission_experience (" + names + ") VALUES (" + values + ")",
        columns.values().toArray());
  }

  /// 插一版修改历史。
  ///
  /// @param postId 帖子 ID
  /// @param revisionNo 版本号
  private void insertRevision(long postId, int revisionNo) {
    jdbcTemplate.update(
        "INSERT INTO itr_post_revision (id, post_id, revision_no, body, fields, written_at,"
            + " replaced_at) VALUES (?, ?, ?, NULL, CAST('{}' AS jsonb), ?, ?)",
        postId * 100 + revisionNo,
        postId,
        revisionNo,
        T0,
        T0);
  }
}
