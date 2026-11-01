package dev.patra.analytics.infra.ingest.support;

import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.newDatabase;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.number;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.text;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.texts;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClickHouseMigratorIT {

  private final Client client = newDatabase();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void migrates_all_scripts_once() {
    ClickHouseMigrator migrator = new ClickHouseMigrator(client);

    migrator.migrate();
    migrator.migrate();

    assertThat(number(client, "SELECT count() AS n FROM schema_migrations")).isEqualTo(5);
    assertThat(texts(client, "SELECT toString(version) FROM schema_migrations ORDER BY version"))
        .containsExactly("1", "2", "3", "4", "5");
    assertThat(
            texts(
                client,
                "SELECT name FROM system.tables WHERE database = currentDatabase() ORDER BY name"))
        .contains(
            "events",
            "dlq_events",
            "article_session_reads",
            "session_quality",
            "daily_person_activity");
  }

  @Test
  void rerun_after_partial_failure_succeeds() throws Exception {
    // 上次启动建好了 events、还没写进 schema_migrations 就失败（超时或进程被杀）
    new ClickHouseMigrator(client).migrate();
    client.execute("TRUNCATE TABLE schema_migrations").get(30, TimeUnit.SECONDS).close();

    new ClickHouseMigrator(client).migrate();

    assertThat(number(client, "SELECT count() AS n FROM schema_migrations")).isEqualTo(5);
  }

  @Test
  void migration_creates_ttl_on_events() {
    new ClickHouseMigrator(client).migrate();

    String ddl =
        text(
            client,
            "SELECT create_table_query AS s FROM system.tables"
                + " WHERE database = currentDatabase() AND name = 'events'");
    assertThat(ddl).contains("TTL").containsAnyOf("INTERVAL 90 DAY", "toIntervalDay(90)");
  }
}
