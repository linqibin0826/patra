package dev.patra.analytics.simulator.backfill;

import static dev.patra.analytics.simulator.support.ClickHouseSimulatorFixture.number;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.ClickHouseSimulatorFixture;
import dev.patra.analytics.simulator.support.Sleeper;
import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import dev.patra.analytics.simulator.truth.ReconcileResult;
import dev.patra.analytics.simulator.truth.Reconciler;
import dev.patra.analytics.simulator.truth.RunScope;
import dev.patra.analytics.simulator.truth.RunSummary;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/// 灌历史写进真的 ClickHouse（服务的表结构与物化视图），再按 PAP-170 的口径对账（spec §13）。
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class BackfillReconcileIT {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final TrafficModel MODEL =
      TrafficModel.load(null, MAPPER).toBuilder().devices(5_000).registeredUsers(500).build();
  private static final Path ARTICLES = smallPool();
  private static final ArticlePool POOL = ArticlePool.load(ARTICLES, 200_000);
  private static final BackfillParams PARAMS =
      new BackfillParams(
          5, 42L, 150_000L, 3, LocalDate.of(2026, 11, 1), null, null, ARTICLES, null);

  @TempDir Path temp;
  private final Client client = ClickHouseSimulatorFixture.migratedDatabaseWithoutMerges();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void backfill_then_reconcile_has_zero_error() {
    RunSummary summary = backfill(client, PARAMS, temp);

    ReconcileResult result =
        new Reconciler(client, new RunScope(5)).reconcile(temp.resolve("truth.csv.gz"), summary);

    assertThat(result.truthViewers()).isGreaterThan(2_000);
    assertThat(result.viewersError()).isZero();
    assertThat(result.fulltextError()).isZero();
    assertThat(result.passed()).isTrue();
    assertThat(result.uniqueEvents()).isEqualTo(summary.deliveredEvents());
    assertThat(result.writtenRows()).isEqualTo(summary.deliveredEvents() + summary.resentEvents());
  }

  @Test
  void written_rows_include_resent_copies() {
    RunSummary summary = backfill(client, PARAMS, temp);

    assertThat(number(client, "SELECT count() AS n FROM events"))
        .isEqualTo(summary.deliveredEvents() + summary.resentEvents());
    assertThat(number(client, "SELECT count() - uniqExact(event_id) AS n FROM events"))
        .isEqualTo(summary.resentEvents());
    assertThat(summary.resentEvents()).isPositive();
  }

  @Test
  void rerun_is_idempotent() {
    backfill(client, PARAMS, temp.resolve("first"));
    long rows = number(client, "SELECT count() AS n FROM events");
    long reads = number(client, "SELECT count() AS n FROM article_session_reads");

    backfill(client, PARAMS, temp.resolve("second"));

    assertThat(number(client, "SELECT count() AS n FROM events")).isEqualTo(rows);
    assertThat(number(client, "SELECT count() AS n FROM article_session_reads")).isEqualTo(reads);
  }

  @Test
  void only_days_then_full_equals_single_full_run() {
    BackfillParams firstDay =
        new BackfillParams(5, 42L, 150_000L, 3, LocalDate.of(2026, 11, 1), 1, null, ARTICLES, null);
    backfill(client, firstDay, temp.resolve("trial"));
    backfill(client, PARAMS, temp.resolve("full"));
    try (Client single = ClickHouseSimulatorFixture.migratedDatabaseWithoutMerges()) {
      backfill(single, PARAMS, temp.resolve("single"));

      assertThat(number(client, "SELECT count() AS n FROM events"))
          .isEqualTo(number(single, "SELECT count() AS n FROM events"));
    }
  }

  @Test
  void one_partition_per_day() {
    backfill(client, PARAMS, temp);

    assertThat(
            number(
                client,
                "SELECT uniqExact(partition) AS n FROM system.parts"
                    + " WHERE active AND database = currentDatabase() AND table = 'events'"))
        .isEqualTo(3);
  }

  private static RunSummary backfill(Client client, BackfillParams params, Path out) {
    return BackfillRunner.create(
            params, MODEL, POOL, new ClickHouseEventSink(client), out, MAPPER, Sleeper.REAL)
        .run();
  }

  private static Path smallPool() {
    return SmallArticlePoolFixture.path();
  }
}
