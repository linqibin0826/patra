package dev.patra.interaction.infra.discussion.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/// discussion 的表约束：评论的 CHECK、正文版本、修改历史的版本号、四个索引。断言违反的约束名，免得别的约束碰巧拦住、
/// 测试假绿。树的完整性（父评论、根评论存在，有回复的评论不整条删）没有外键，由应用在楼锁内保证。
class DiscussionMigrationIT extends DiscussionPersistenceITSupport {

  /// 四个索引都在。
  @Test
  void should_create_tables_and_indexes() {
    List<String> indexes =
        jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'itr_comment'",
            String.class);
    assertThat(indexes)
        .contains(
            "idx_itr_comment_roots",
            "idx_itr_comment_thread",
            "idx_itr_comment_parent",
            "idx_itr_comment_participants");
  }

  /// 状态、树的形状、正文与删除时刻的配对。
  @Test
  void should_enforce_comment_checks() {
    insertRaw(10, 10, null, "ACTIVE", "根", null);

    assertViolates(() -> insertRaw(1, 1, null, "HIDDEN", null, null), "ck_itr_comment_state");
    assertViolates(() -> insertRaw(11, 11, 10L, "ACTIVE", "x", null), "ck_itr_comment_tree");
    assertViolates(() -> insertRaw(12, 12, null, "ACTIVE", null, null), "ck_itr_comment_body");
    assertViolates(() -> insertRaw(13, 13, null, "DELETED", "x", T0), "ck_itr_comment_body");
    assertViolates(
        () -> insertRaw(14, 14, null, "DELETED", null, null), "ck_itr_comment_deleted_at");
  }

  /// 正文版本：不写时是 1，必须为正。
  @Test
  void should_add_comment_version_with_default_one() {
    insertRaw(10, 10, null, "ACTIVE", "根", null);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT body_version FROM itr_comment WHERE id = 10", Integer.class))
        .isEqualTo(1);
    assertViolates(
        () -> jdbcTemplate.update("UPDATE itr_comment SET body_version = 0 WHERE id = 10"),
        "ck_itr_comment_body_version");
  }

  /// 修改历史的版本号从 1 开始，同一评论不重号。
  @Test
  void should_reject_non_positive_revision_no() {
    insertRaw(10, 10, null, "ACTIVE", "根", null);

    assertViolates(
        () ->
            jdbcTemplate.update(
                "INSERT INTO itr_comment_revision (id, comment_id, revision_no, body, version_at)"
                    + " VALUES (1, 10, 0, '旧', ?)",
                Timestamp.from(T0)),
        "ck_itr_comment_revision_no");
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
