package dev.patra.interaction.infra.discussion.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/// discussion 的两张表：四个 CHECK、树的两个自引用外键、修改历史随评论级联删除、四个索引（spec 4.1、4.2）。断言违反的
/// 约束名，免得别的约束碰巧拦住、测试假绿。
class DiscussionMigrationIT extends DiscussionPersistenceITSupport {

  /// 四个索引都在。
  @Test
  void should_create_tables_and_indexes() {
    List<String> indexes =
        jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'discussion'", String.class);

    assertThat(indexes)
        .contains(
            "idx_comment_roots",
            "idx_comment_thread",
            "idx_comment_parent",
            "idx_comment_participants");
  }

  /// 状态、树的形状、正文与删除时刻的配对。
  @Test
  void should_enforce_comment_checks() {
    insertRaw(10, 10, null, "ACTIVE", "根", null);

    assertViolates(() -> insertRaw(1, 1, null, "HIDDEN", null, null), "ck_comment_state");
    assertViolates(() -> insertRaw(11, 11, 10L, "ACTIVE", "x", null), "ck_comment_tree");
    assertViolates(() -> insertRaw(12, 12, null, "ACTIVE", null, null), "ck_comment_body");
    assertViolates(() -> insertRaw(13, 13, null, "DELETED", "x", T0), "ck_comment_body");
    assertViolates(() -> insertRaw(14, 14, null, "DELETED", null, null), "ck_comment_deleted_at");
  }

  /// 正文版本（PAP-141）：不写时是 1，必须为正。
  @Test
  void should_add_comment_version_with_default_one() {
    insertRaw(10, 10, null, "ACTIVE", "根", null);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT version FROM discussion.comment WHERE comment_id = 10", Integer.class))
        .isEqualTo(1);
    assertViolates(
        () ->
            jdbcTemplate.update("UPDATE discussion.comment SET version = 0 WHERE comment_id = 10"),
        "ck_comment_version");
  }

  /// 父评论、根评论必须存在；还有回复的评论删不掉；删评论连带删修改历史。
  @Test
  void should_enforce_tree_foreign_keys() {
    insertRaw(10, 10, null, "ACTIVE", "根", null);
    insertRaw(30, 10, 10L, "ACTIVE", "A", null);
    insertRaw(31, 10, 30L, "ACTIVE", "B", null);
    jdbcTemplate.update(
        "INSERT INTO discussion.comment_revision (comment_id, revision_no, body, version_at)"
            + " VALUES (31, 1, '旧', ?)",
        Timestamp.from(T0));

    assertViolates(() -> insertRaw(20, 10, 999L, "ACTIVE", "x", null), "fk_comment_parent");
    assertViolates(() -> insertRaw(21, 998, 10L, "ACTIVE", "x", null), "fk_comment_root");
    assertViolates(
        () -> jdbcTemplate.update("DELETE FROM discussion.comment WHERE comment_id = 30"),
        "fk_comment_parent");

    jdbcTemplate.update("DELETE FROM discussion.comment WHERE comment_id = 31");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM discussion.comment_revision", Long.class))
        .isZero();
  }

  /// 版本号从 1 开始。
  @Test
  void should_reject_non_positive_revision_no() {
    insertRaw(10, 10, null, "ACTIVE", "根", null);

    assertViolates(
        () ->
            jdbcTemplate.update(
                "INSERT INTO discussion.comment_revision (comment_id, revision_no, body,"
                    + " version_at) VALUES (10, 0, '旧', ?)",
                Timestamp.from(T0)),
        "ck_comment_revision_no");
  }

  /// 断言违反了指定的约束。
  ///
  /// @param write 写操作
  /// @param constraint 约束名
  private static void assertViolates(ThrowingCallable write, String constraint) {
    assertThatThrownBy(write)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }
}
