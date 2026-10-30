package dev.patra.interaction.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import java.util.Map;
import java.util.stream.Collectors;
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

/// PAP-133 的收藏人数统计表：建表时按现有文献库条目回填，计数不能为负。
///
/// 共用容器里的主库已经迁到最新，造不了旧数据：另建一个库，先迁到改动前的版本、造文献库条目，再迁到最新。
@SpringBootTest(classes = InteractionITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("收藏人数统计表的迁移与回填")
class CollectionStatsMigrationIT {

  private static final String DATABASE = "collection_stats_migration";

  /// 改动前最后一条迁移的版本。
  private static final String BEFORE = "20261029200200";

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

  /// 每篇的收藏人数等于它的文献库条目数；没人收藏的不建行。
  @Test
  void should_backfill_collector_counts() {
    Map<Long, Integer> counts =
        jdbc
            .queryForList("SELECT subject_id, collector_count FROM collection.subject_stats")
            .stream()
            .collect(
                Collectors.toMap(
                    row -> ((Number) row.get("subject_id")).longValue(),
                    row -> ((Number) row.get("collector_count")).intValue()));

    assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(11L, 2, 12L, 1));
  }

  /// 计数不能为负。
  @Test
  void should_keep_collector_count_non_negative() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO collection.subject_stats (subject_id, collector_count)"
                        + " VALUES (13, -1)"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_subject_stats_collectors");
  }

  /// 与服务同一套 Flyway 设置。
  ///
  /// @param target 目标库
  /// @return 配置
  private static FluentConfiguration flyway(DataSource target) {
    return Flyway.configure().dataSource(target).locations("classpath:db/migration");
  }

  /// 造数据：用户 1 收藏了对象 11、12，用户 2 收藏了对象 11（落在不同分区）。
  private void seed() {
    jdbc.update(
        "INSERT INTO collection.library_item (user_id, subject_id, collected_at) VALUES"
            + " (1, 11, now()), (1, 12, now()), (2, 11, now())");
  }
}
