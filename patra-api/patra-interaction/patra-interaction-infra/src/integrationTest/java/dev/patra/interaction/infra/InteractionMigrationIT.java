package dev.patra.interaction.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// Flyway 迁移：每个子域一个 schema，整个服务一份历史，版本号是时间戳。
@SpringBootTest(classes = InteractionITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("interaction 的 Flyway 迁移")
class InteractionMigrationIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  /// 除 PG 自带的 schema 外，恰好是 public 与两个子域的 schema。
  @Test
  void should_create_one_schema_per_subdomain() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata "
                    + "WHERE schema_name NOT LIKE 'pg\\_%' AND schema_name <> 'information_schema'",
                String.class))
        .containsExactlyInAnyOrder("public", "subject", "collection");
  }

  /// Flyway 历史表只有一张，在 public。
  @Test
  void should_keep_a_single_flyway_history_in_public() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'flyway_schema_history'",
                String.class))
        .containsExactly("public");
  }

  /// 两个子域的脚本都执行成功，版本号就是它们的时间戳。
  @Test
  void should_record_both_migrations_with_timestamp_versions() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT version FROM public.flyway_schema_history "
                    + "WHERE success ORDER BY installed_rank",
                String.class))
        .containsExactly("20261024100000", "20261024100100");
  }
}
