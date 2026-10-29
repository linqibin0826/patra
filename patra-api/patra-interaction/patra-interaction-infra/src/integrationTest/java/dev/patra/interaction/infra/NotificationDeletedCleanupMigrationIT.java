package dev.patra.interaction.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// PAP-127 的数据迁移：删掉已标删除的提醒和已删除经验下的提醒，去掉删除标记列与约束，补对象索引。
///
/// 共用容器里的主库已经迁到最新，造不了旧数据：另建一个库，先迁到改动前的版本、造三类数据，再迁到最新。
@SpringBootTest(classes = InteractionITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("删除已删除内容的提醒的迁移")
class NotificationDeletedCleanupMigrationIT {

  private static final String DATABASE = "notification_cleanup_migration";

  /// 改动前最后一条迁移的版本。
  private static final String BEFORE = "20261027190100";

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  /// 新建库，迁到改动前、造数据，再迁到最新。
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

  /// 已标删除的、已删除经验下的都删掉，正常文献下的留下。
  @Test
  void should_delete_flagged_and_deleted_post_notifications() {
    assertThat(
            jdbc.queryForList(
                "SELECT notification_id FROM notification.notification ORDER BY 1", Long.class))
        .containsExactly(3L);
  }

  /// 删除标记列与它的约束都去掉了。
  @Test
  void should_drop_comment_deleted_column_and_constraint() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'notification'"
                    + " AND table_name = 'notification' AND column_name = 'comment_deleted'",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_notification_deleted_excerpt'",
                Long.class))
        .isZero();
  }

  /// 按对象删提醒有索引可走。
  @Test
  void should_index_subject_id() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'notification'"
                    + " AND tablename = 'notification' AND indexname = 'idx_notification_subject'",
                Long.class))
        .isEqualTo(1L);
  }

  /// 与服务同一套 Flyway 设置。
  ///
  /// @param target 目标库
  /// @return 配置
  private static FluentConfiguration flyway(DataSource target) {
    return Flyway.configure().dataSource(target).locations("classpath:db/migration");
  }

  /// 造数据：对象 10 是已删除的经验，对象 20 是正常文献；提醒 1 已标删除，提醒 2 挂在经验下，提醒 3 正常。
  private void seed() {
    jdbc.update(
        "INSERT INTO subject.registry (subject_id, target_type, natural_key, source_id, state,"
            + " snapshot, snapshot_at, owner_user_id, owner_anonymous) VALUES"
            + " (10, 3, 'post:900', 900, 'UNAVAILABLE', '{}', now(), 7, false),"
            + " (20, 1, 'pmid:27940189', 5000, 'ACTIVE', '{}', now(), NULL, NULL)");
    jdbc.update(
        "INSERT INTO notification.notification (recipient_id, notification_id, type, actor_id,"
            + " actor_anonymous, subject_id, comment_id, excerpt, comment_deleted, created_at)"
            + " VALUES"
            + " (1, 1, 'COMMENT_REPLY', 2, false, 20, 501, NULL, true, now()),"
            + " (1, 2, 'POST_COMMENT', 2, false, 10, 502, '问一下', false, now()),"
            + " (1, 3, 'COMMENT_REPLY', 2, false, 20, 503, '同意', false, now())");
  }
}
