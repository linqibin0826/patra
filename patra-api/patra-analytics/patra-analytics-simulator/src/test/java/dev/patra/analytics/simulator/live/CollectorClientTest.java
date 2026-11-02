package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CollectorClientTest {

  private static final String BODY_OF_TWO = "{\"events\":[{\"a\":1},{\"a\":2}]}";

  private final FakeCollector collector = new FakeCollector();
  private final LiveStats stats = new LiveStats(Clock.systemUTC());
  private final AtomicLong slept = new AtomicLong();

  @AfterEach
  void close() {
    collector.close();
  }

  private CollectorClient client(int maxInFlight) {
    return new CollectorClient(
        collector.uri(), maxInFlight, 5, Duration.ofSeconds(5), stats, slept::addAndGet);
  }

  @Test
  void retries_503_with_identical_body_then_delivers() {
    collector.statuses.add(503);
    collector.statuses.add(503);

    SendOutcome outcome = client(4).send(BODY_OF_TWO, 2);

    assertThat(outcome).isEqualTo(new SendOutcome.Delivered(2, 0));
    assertThat(collector.bodies).hasSize(3).containsOnly(BODY_OF_TWO);
    assertThat(slept.get()).isEqualTo(200 + 400);
    assertThat(stats.totals().delivered()).isEqualTo(2);
  }

  @Test
  void does_not_retry_400() {
    collector.statuses.add(400);

    SendOutcome outcome = client(4).send(BODY_OF_TWO, 2);

    assertThat(outcome).isInstanceOf(SendOutcome.Failed.class);
    assertThat(((SendOutcome.Failed) outcome).status()).isEqualTo(400);
    assertThat(((SendOutcome.Failed) outcome).uncertain()).isFalse();
    assertThat(collector.bodies).hasSize(1);
    assertThat(stats.totals().failed()).isEqualTo(2);
    assertThat(stats.totals().uncertain()).isZero();
  }

  @Test
  void gives_up_after_max_attempts() {
    for (int i = 0; i < 10; i++) {
      collector.statuses.add(503);
    }

    SendOutcome outcome = client(4).send(BODY_OF_TWO, 2);

    assertThat(outcome).isInstanceOf(SendOutcome.Failed.class);
    assertThat(((SendOutcome.Failed) outcome).uncertain()).isTrue();
    assertThat(collector.bodies).hasSize(5);
    assertThat(stats.totals().failed()).isZero();
    assertThat(stats.totals().uncertain()).isEqualTo(2);
  }

  @Test
  void connection_error_is_retried() {
    CollectorClient unreachable =
        new CollectorClient(
            URI.create("http://127.0.0.1:1/events"),
            4,
            3,
            Duration.ofSeconds(1),
            stats,
            slept::addAndGet);

    SendOutcome outcome = unreachable.send(BODY_OF_TWO, 2);

    assertThat(outcome).isEqualTo(new SendOutcome.Failed(-1, "重试 3 次仍失败"));
    assertThat(slept.get()).isEqualTo(200 + 400);
  }

  @Test
  void in_flight_never_exceeds_limit() throws InterruptedException {
    collector.delayMillis = 200;
    CollectorClient client = client(8);
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      threads.add(Thread.ofVirtual().start(() -> client.send(BODY_OF_TWO, 2)));
    }
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(collector.maxConcurrent.get()).isBetween(2, 8);
    assertThat(stats.totals().delivered()).isEqualTo(200);
  }

  @Test
  void rejected_count_is_reported() {
    collector.rejected = 1;

    SendOutcome outcome = client(4).send(BODY_OF_TWO, 2);

    assertThat(outcome).isEqualTo(new SendOutcome.Delivered(1, 1));
    assertThat(stats.totals().rejected()).isEqualTo(1);
  }
}
