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
}
