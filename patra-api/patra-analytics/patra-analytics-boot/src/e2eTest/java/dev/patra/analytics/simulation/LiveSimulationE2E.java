package dev.patra.analytics.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.analytics.simulator.live.CollectorClient;
import dev.patra.analytics.simulator.live.LatencySummary;
import dev.patra.analytics.simulator.live.LiveParams;
import dev.patra.analytics.simulator.live.LiveRunner;
import dev.patra.analytics.simulator.live.LiveStats;
import dev.patra.analytics.simulator.live.RateStep;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.Sleeper;
import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import dev.patra.analytics.simulator.truth.ReconcileResult;
import dev.patra.analytics.simulator.truth.Reconciler;
import dev.patra.analytics.simulator.truth.RunScope;
import dev.patra.analytics.simulator.truth.RunSummary;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.json.JsonMapper;

/// 实时压测的本机端到端（PAP-171 spec §11）：真的 analytics 服务（采集 → Kafka → 入库 → ClickHouse 物化视图，
/// 依赖全用 Testcontainers，不连 mini），模拟器按速率经 HTTP 打采集端点，等入库追平后按 PAP-170 的口径对账。
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "patra.analytics.ingest.enabled=true",
      "patra.analytics.kafka.raw-topic=e2e-live.raw",
      "patra.analytics.kafka.dlq-topic=e2e-live.dlq"
    })
@ContextConfiguration(
    initializers = {
      AnalyticsITPostgreSQLContainerInitializer.class,
      ClickHouseContainerInitializer.class,
      KafkaContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
class LiveSimulationE2E {

  private static final int RUN = 801;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @LocalServerPort private int port;
  @Autowired private Client clickhouse;
  @TempDir Path out;

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void live_run_reconciles_against_the_real_pipeline() {
    TrafficModel model =
        TrafficModel.load(null, MAPPER).toBuilder().devices(20_000).registeredUsers(2_000).build();
    ArticlePool pool = ArticlePool.load(SmallArticlePoolFixture.path(), 200_000);
    URI collector = URI.create("http://localhost:" + port + "/events");
    LiveStats stats = new LiveStats(Clock.systemUTC());
    CollectorClient client =
        new CollectorClient(collector, 128, 5, Duration.ofSeconds(6), stats, Sleeper.REAL);
    LiveParams params =
        new LiveParams(
            RUN,
            7L,
            List.of(new RateStep(300, Duration.ofSeconds(60))),
            collector,
            128,
            true,
            Duration.ofSeconds(20));

    RunSummary summary =
        new LiveRunner(
                params, model, pool, client, stats, Clock.systemUTC(), Sleeper.REAL, MAPPER, out)
            .run();
    Reconciler reconciler = new Reconciler(clickhouse, new RunScope(RUN));
    awaitIngested(reconciler, summary);
    ReconcileResult result = reconciler.reconcile(out.resolve("truth.csv.gz"), summary);
    System.getLogger(LiveSimulationE2E.class.getName())
        .log(System.Logger.Level.INFO, "端到端对账：{0}；请求 {1}；摘要 {2}", result, stats.totals(), summary);

    assertThat(summary.failedEvents()).isZero();
    assertThat(stats.totals().rejected()).isZero();
    assertThat(windowRate())
        .as("到达窗口内的送达速率（目标 300 条/秒，预热后一开始就是稳态）")
        .isBetween(300 * 0.6, 300 * 1.4);
    assertThat(result.truthViewers()).isPositive();
    assertThat(result.uniqueEvents()).isEqualTo(summary.deliveredEvents());
    assertThat(result.viewersError())
        .as(result.sampleMismatches().toString())
        .isLessThanOrEqualTo(0.01);
    assertThat(result.fulltextError())
        .as(result.sampleMismatches().toString())
        .isLessThanOrEqualTo(0.01);
  }

  /// 到达窗口内每秒送达条数的均值（不含停发后的收尾）。
  ///
  /// @return 条/秒
  private double windowRate() {
    try {
      LatencySummary window = LatencySummary.read(out.resolve("latency.json"), MAPPER);
      return Files.readAllLines(out.resolve("live-stats.csv")).stream()
          .skip(1)
          .map(line -> line.split(","))
          .filter(row -> !Instant.parse(row[0]).isBefore(window.arrivalStart()))
          .filter(row -> Instant.parse(row[0]).isBefore(window.arrivalEnd()))
          .mapToLong(row -> Long.parseLong(row[2]))
          .average()
          .orElse(0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 等入库追平：ClickHouse 里本运行的去重事件数达到送达数（最多 90 秒）。
  ///
  /// @param reconciler 对账器
  /// @param summary 运行摘要
  private static void awaitIngested(Reconciler reconciler, RunSummary summary) {
    long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
    while (System.nanoTime() < deadline) {
      long ingested =
          summary.eventsByDay().keySet().stream()
              .mapToLong(day -> reconciler.uniqueEvents(LocalDate.parse(day)))
              .sum();
      if (ingested >= summary.deliveredEvents()) {
        return;
      }
      LockSupport.parkNanos(Duration.ofSeconds(1).toNanos());
    }
  }
}
