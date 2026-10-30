package dev.patra.interaction.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// PAP-132 的讨论统计表：建表、约束，以及按现有评论回填楼统计与对象统计。
///
/// 共用容器里的主库已经迁到最新，造不了旧数据：另建一个库，先迁到改动前的版本、造评论，再迁到最新。
@SpringBootTest(classes = InteractionITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("讨论统计表的迁移与回填")
class DiscussionStatsMigrationIT {

  private static final String DATABASE = "discussion_stats_migration";

  /// 改动前最后一条迁移的版本。
  private static final String BEFORE = "20261029200200";

  private static final Instant T0 = Instant.parse("2026-10-27T01:00:00Z");

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  /// 新建库，迁到改动前、造评论，再迁到最新。
  @BeforeAll
  void migrate() {
    HikariDataSource main = (HikariDataSource) dataSource;
    JdbcTemplate admin = new JdbcTemplate(main);
    admin.execute("DROP DATABASE IF EXISTS " + DATABASE);
    admin.execute("CREATE DATABASE " + DATABASE);
    DriverManagerDataSource isolated =
        new DriverManagerDataSource(
            main.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DATABASE + "$1"),
            main.getUsername(),
            main.getPassword());
    jdbc = new JdbcTemplate(isolated);

    flyway(isolated).target(BEFORE).load().migrate();
    seed();
    flyway(isolated).load().migrate();
  }

  /// 楼统计只数未删除的回复（任意深度），时间取它们中最新的；没有回复的根没有行。
  @Test
  void should_backfill_thread_stats_from_active_replies() {
    assertThat(
            jdbc.queryForList(
                "SELECT root_id, reply_count, last_reply_at FROM discussion.thread_stats"
                    + " ORDER BY root_id"))
        .containsExactly(
            Map.of("root_id", 1L, "reply_count", 2, "last_reply_at", at(3)),
            Map.of("root_id", 5L, "reply_count", 1, "last_reply_at", at(4)));
  }

  /// 对象统计数未删除的根评论与回复，占位不算。
  @Test
  void should_backfill_subject_stats_from_active_comments() {
    assertThat(
            jdbc.queryForList(
                "SELECT subject_id, comment_count FROM discussion.subject_stats ORDER BY 1"))
        .containsExactly(
            Map.of("subject_id", 11L, "comment_count", 4),
            Map.of("subject_id", 12L, "comment_count", 1));
  }

  /// 回填结果与按明细重算一致。
  @Test
  void should_match_recount_after_backfill() {
    assertThat(
            jdbc.queryForList(
                "SELECT root_id, reply_count, last_reply_at FROM discussion.thread_stats"
                    + " WHERE reply_count > 0 ORDER BY 1"))
        .isEqualTo(
            jdbc.queryForList(
                "SELECT root_id, count(*)::int AS reply_count, max(created_at) AS last_reply_at"
                    + " FROM discussion.comment WHERE parent_id IS NOT NULL AND state = 'ACTIVE'"
                    + " GROUP BY root_id ORDER BY 1"));
    assertThat(
            jdbc.queryForList(
                "SELECT subject_id, comment_count FROM discussion.subject_stats"
                    + " WHERE comment_count > 0 ORDER BY 1"))
        .isEqualTo(
            jdbc.queryForList(
                "SELECT subject_id, count(*)::int AS comment_count FROM discussion.comment"
                    + " WHERE state = 'ACTIVE' GROUP BY subject_id ORDER BY 1"));
  }

  /// 回复数与最后回复时间要么都有、要么都空；计数不为负。
  @Test
  void should_reject_inconsistent_stats() {
    List.of(
            List.<Object>of(90L, 0, Timestamp.from(T0), "ck_thread_stats_last"),
            List.<Object>of(91L, 1, "", "ck_thread_stats_last"),
            List.<Object>of(92L, -1, "", "ck_thread_stats_count"))
        .forEach(
            row ->
                assertThatThrownBy(
                        () ->
                            jdbc.update(
                                "INSERT INTO discussion.thread_stats VALUES (?, ?, ?)",
                                row.get(0),
                                row.get(1),
                                row.get(2) instanceof Timestamp ts ? ts : null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining((String) row.get(3)));
    assertThatThrownBy(() -> jdbc.update("INSERT INTO discussion.subject_stats VALUES (93, -1)"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_subject_stats_count");
  }

  /// 与服务同一套 Flyway 设置。
  ///
  /// @param target 目标库
  /// @return 配置
  private static FluentConfiguration flyway(DataSource target) {
    return Flyway.configure().dataSource(target).locations("classpath:db/migration");
  }

  /// 第 n 秒的时刻（读回的 `timestamptz` 列是 `Timestamp`）。
  ///
  /// @param seconds 相对 `T0` 的秒数
  /// @return 时刻
  private static Timestamp at(int seconds) {
    return Timestamp.from(T0.plusSeconds(seconds));
  }

  /// 造评论：对象 11 下根 1 有回复 2（正常）、3（占位）、4（回复 2，正常）；根 5 是占位，有回复 6（正常）；
  /// 对象 12 下根 7 没有回复。
  private void seed() {
    comment(1, 11, 1, null, "ACTIVE", 0);
    comment(2, 11, 1, 1L, "ACTIVE", 1);
    comment(3, 11, 1, 1L, "DELETED", 2);
    comment(4, 11, 1, 2L, "ACTIVE", 3);
    comment(5, 11, 5, null, "DELETED", 0);
    comment(6, 11, 5, 5L, "ACTIVE", 4);
    comment(7, 12, 7, null, "ACTIVE", 0);
  }

  /// 插一条评论。
  ///
  /// @param id 评论 ID
  /// @param subjectId 对象
  /// @param rootId 根评论
  /// @param parentId 父评论，根评论为 `null`
  /// @param state 状态
  /// @param seconds 发表时刻相对 `T0` 的秒数
  private void comment(
      long id, long subjectId, long rootId, Long parentId, String state, int seconds) {
    boolean active = "ACTIVE".equals(state);
    jdbc.update(
        "INSERT INTO discussion.comment (comment_id, subject_id, root_id, parent_id, author_id,"
            + " state, body, created_at, deleted_at) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?)",
        id,
        subjectId,
        rootId,
        parentId,
        state,
        active ? "正文" : null,
        at(seconds),
        active ? null : at(seconds + 10));
  }
}
