package dev.patra.notification.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// Flyway 迁移：整个服务一份历史，在 public；每个子域随自己的第一张表建 schema，schema 名就是子域名。
@SpringBootTest(classes = NotificationITBootstrap.class)
@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("notification 的 Flyway 迁移")
class NotificationMigrationIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  /// Flyway 历史表只有一张，在 public：防止有人设 `spring.flyway.schemas` 把历史挪进某个子域的 schema。
  @Test
  void should_keep_a_single_flyway_history_in_public() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'flyway_schema_history'",
                String.class))
        .containsExactly("public");
  }

  /// 除 PG 自带的 schema 外，只有 public 与子域清单里的 schema。新子域随自己的迁移建 schema，不用改这条测试。
  @Test
  void should_only_have_public_and_subdomain_schemas() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata "
                    + "WHERE schema_name NOT LIKE 'pg\\_%' AND schema_name <> 'information_schema'",
                String.class))
        .contains("public")
        .isSubsetOf("public", "kernel", "inbox", "preference", "mail", "realtime");
  }
}
