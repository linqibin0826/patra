package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.Sleeper;
import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import dev.patra.analytics.simulator.truth.RunSummary;
import dev.patra.analytics.simulator.truth.TruthFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class LiveRunnerTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final TrafficModel MODEL =
      TrafficModel.load(null, MAPPER).toBuilder().devices(5_000).registeredUsers(500).build();
  private static final ArticlePool POOL = ArticlePool.load(SmallArticlePoolFixture.path(), 200_000);

  @TempDir Path temp;
  private final FakeCollector collector = new FakeCollector();

  @AfterEach
  void close() {
    collector.close();
  }

  @Test
  @Timeout(60)
  void arrival_rate_matches_target_and_outputs_written() throws Exception {
    LiveParams params =
        new LiveParams(
            11,
            7L,
            List.of(new RateStep(3_000, Duration.ofSeconds(3))),
            collector.uri(),
            64,
            false,
            Duration.ofSeconds(30));
    LiveStats stats = new LiveStats(Clock.systemUTC());
    CollectorClient client =
        new CollectorClient(collector.uri(), 64, 3, Duration.ofSeconds(5), stats, millis -> {});

    LiveRunner runner =
        new LiveRunner(
            params, MODEL, POOL, client, stats, Clock.systemUTC(), millis -> {}, MAPPER, temp);
    RunSummary summary = runner.run();

    double expectedSessions = 3 * 3_000 / runner.meanEvents();
    long sessions = summary.sessionsByKind().values().stream().mapToLong(Long::longValue).sum();
    assertThat(sessions)
        .isBetween((long) (expectedSessions * 0.8), (long) (expectedSessions * 1.2));
    assertThat(summary.mode()).isEqualTo("live");
    assertThat(summary.deliveredEvents()).isPositive();
    assertThat(summary.failedEvents()).isZero();
    assertThat(summary.resentEvents()).isPositive();
    assertThat(summary.botEventsByReason()).containsKey("ua_list");
    assertThat(Files.readAllLines(temp.resolve("live-stats.csv"))).hasSizeGreaterThan(2);
    assertThat(TruthFile.read(temp.resolve("truth.csv.gz"))).isNotEmpty();
    assertThat(RunSummary.read(temp.resolve("summary.json"), MAPPER)).isEqualTo(summary);
    LatencySummary latency = LatencySummary.read(temp.resolve("latency.json"), MAPPER);
    assertThat(latency.requests()).isEqualTo(stats.totals().requests());
    assertThat(latency.p99Millis()).isGreaterThanOrEqualTo(latency.p50Millis());
  }

  @Test
  @Timeout(30)
  void sessions_still_running_at_drain_deadline_are_stopped() throws InterruptedException {
    LiveParams params =
        new LiveParams(
            12,
            7L,
            List.of(new RateStep(500, Duration.ofSeconds(2))),
            collector.uri(),
            64,
            false,
            Duration.ofSeconds(1));
    LiveStats stats = new LiveStats(Clock.systemUTC());
    CollectorClient client =
        new CollectorClient(collector.uri(), 64, 3, Duration.ofSeconds(5), stats, millis -> {});
    long started = System.nanoTime();

    RunSummary summary =
        new LiveRunner(
                params, MODEL, POOL, client, stats, Clock.systemUTC(), Sleeper.REAL, MAPPER, temp)
            .run();

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(15));
    int requestsAtReturn = collector.bodies.size();
    Thread.sleep(3_000);
    assertThat(collector.bodies).as("run() 返回后不该再有会话在发").hasSize(requestsAtReturn);
    assertThat(summary.deliveredEvents()).isPositive();
    assertThat(summary.eventsByDay().values().stream().mapToLong(Long::longValue).sum())
        .isEqualTo(summary.deliveredEvents());
  }

  @Test
  @Timeout(60)
  void warm_start_reaches_target_rate_from_the_first_seconds() throws Exception {
    // 开环从零在线起步要等最长的会话走完（几十分钟）才到稳态；预热后一开始就该接近目标速率
    LiveParams params =
        new LiveParams(
            13,
            7L,
            List.of(new RateStep(200, Duration.ofSeconds(6))),
            collector.uri(),
            256,
            true,
            Duration.ofSeconds(1));
    LiveStats stats = new LiveStats(Clock.systemUTC());
    CollectorClient client =
        new CollectorClient(collector.uri(), 256, 3, Duration.ofSeconds(5), stats, millis -> {});

    new LiveRunner(
            params, MODEL, POOL, client, stats, Clock.systemUTC(), Sleeper.REAL, MAPPER, temp)
        .run();

    LatencySummary window = LatencySummary.read(temp.resolve("latency.json"), MAPPER);
    double rate =
        Files.readAllLines(temp.resolve("live-stats.csv")).stream()
            .skip(1)
            .map(line -> line.split(","))
            .filter(row -> !Instant.parse(row[0]).isBefore(window.arrivalStart().plusSeconds(1)))
            .filter(row -> Instant.parse(row[0]).isBefore(window.arrivalEnd()))
            .mapToLong(row -> Long.parseLong(row[2]))
            .average()
            .orElse(0);
    // 不预热时头几秒只有约 15%；本机负载高时真实时间的速率会抖，区间放宽到 0.5～1.5 倍仍能分出两者
    assertThat(rate).isBetween(200 * 0.5, 200 * 1.5);
  }
}
