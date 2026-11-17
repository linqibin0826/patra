package dev.patra.interaction.infra.reaction.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/// reaction 的表约束：类型只有两种、不能记在自己名下、计数非负；按目标删除走索引。
class ReactionMigrationIT extends ReactionPersistenceITSupport {

  @Test
  void should_enforce_helpful_checks() {
    insertHelpful(1L, "COMMENT", 9L);
    insertHelpful(1L, "POST", null);

    assertThatThrownBy(() -> insertHelpful(2L, "LIKE", 9L))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_itr_helpful_target_type");
    assertThatThrownBy(() -> insertHelpful(9L, "COMMENT", 9L))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_itr_helpful_not_self");
    assertThatThrownBy(() -> insertHelpful(1L, "COMMENT", 9L))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void should_keep_counters_non_negative() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO itr_helpful_target_stats (id, target_type, target_id, helpful_count)"
                        + " VALUES (1, 'COMMENT', 1, -1)"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_itr_helpful_target_stats_count");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO itr_helpful_target_stats (id, target_type, target_id, helpful_count)"
                        + " VALUES (2, 'LIKE', 1, 0)"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_itr_helpful_target_stats_type");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO itr_helpful_user_stats (id, user_id, helpful_received) VALUES (1, 1, -1)"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_itr_helpful_user_stats_received");
  }

  @Test
  void should_index_helpful_by_target() {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_itr_helpful_target'",
                String.class))
        .contains("(target_type, target_id)");
  }

  /// 不经仓储直接插一条明细。
  ///
  /// @param userId 点的人
  /// @param type 种类
  /// @param credited 受益人，可空
  private void insertHelpful(long userId, String type, Long credited) {
    jdbcTemplate.update(
        "INSERT INTO itr_helpful (id, user_id, target_type, target_id, credited_user_id)"
            + " VALUES ((SELECT COALESCE(max(id), 0) + 1 FROM itr_helpful), ?, ?, 100, ?)",
        userId,
        type,
        credited);
  }
}
