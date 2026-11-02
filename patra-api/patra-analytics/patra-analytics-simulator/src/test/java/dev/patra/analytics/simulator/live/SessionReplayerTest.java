package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.CollectEnvelopeWriter;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SyntheticIds;
import dev.patra.analytics.simulator.event.Timestamps;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.Duplicates;
import dev.patra.analytics.simulator.model.Population;
import dev.patra.analytics.simulator.model.SessionGenerator;
import dev.patra.analytics.simulator.model.SimSession;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SessionReplayerTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final TrafficModel MODEL =
      TrafficModel.load(null, MAPPER).toBuilder().devices(2_000).registeredUsers(200).build();
  private static final ArticlePool POOL = ArticlePool.load(SmallArticlePoolFixture.path(), 200_000);
  private static final Population POPULATION = Population.build(MODEL, new SyntheticIds(9), 1);
  private static final SessionGenerator GENERATOR = new SessionGenerator(MODEL, POOL);
  private static final Instant START = Instant.parse("2026-11-02T02:00:00Z");

  private final FakeCollector collector = new FakeCollector();
  private final MutableClock clock = new MutableClock(START);
  private final LiveStats stats = new LiveStats(Clock.systemUTC());

  @AfterEach
  void close() {
    collector.close();
  }

  private SessionReplayer replayer() {
    CollectorClient client =
        new CollectorClient(collector.uri(), 4, 2, Duration.ofSeconds(5), stats, millis -> {});
    return new SessionReplayer(
        new CollectEnvelopeWriter(MAPPER), client, clock, clock.sleeper(), Runnable::run);
  }

  private static SimSession session(int seed) {
    return GENERATOR.human(POPULATION.person(seed), START, new SplittableRandom(seed));
  }

  @Test
  void groups_events_into_requests_by_sent_at_and_source() {
    SimSession session = session(3);

    replayer().replay(session, new SplittableRandom(1));

    long groups =
        session.events().stream().map(e -> e.sentAt() + "#" + e.name().source()).distinct().count();
    assertThat(collector.bodies).hasSize((int) groups + resent(session));
  }

  @Test
  void sleeps_until_planned_offset() {
    SimSession session = session(4);

    replayer().replay(session, new SplittableRandom(1));

    Instant first =
        session.events().stream().map(SimEvent::sentAt).min(Instant::compareTo).orElseThrow();
    Instant last =
        session.events().stream().map(SimEvent::sentAt).max(Instant::compareTo).orElseThrow();
    assertThat(Duration.ofMillis(clock.slept))
        .isGreaterThanOrEqualTo(Duration.between(first, last));
  }

  @Test
  void sent_at_is_actual_send_time() {
    replayer().replay(session(5), new SplittableRandom(1));

    for (String body : collector.bodies) {
      for (JsonNode event : MAPPER.readTree(body).get("events")) {
        assertThat(Instant.parse(event.get("sent_at").asString()))
            .isAfterOrEqualTo(START)
            .isBeforeOrEqualTo(clock.instant());
      }
    }
  }

  @Test
  void only_delivered_events_returned() {
    SimSession session = session(6);
    collector.statuses.add(202);
    collector.statuses.add(400);

    List<SimEvent> delivered = replayer().replay(session, new SplittableRandom(1));

    JsonNode second = MAPPER.readTree(collector.bodies.get(1)).get("events");
    Set<String> lost = new HashSet<>();
    second.forEach(e -> lost.add(e.get("event_id").asString()));
    assertThat(delivered).noneMatch(e -> lost.contains(e.eventId().toString()));
    assertThat(delivered).hasSize(session.events().size() - lost.size());
  }

  @Test
  void resends_chosen_events_in_separate_request() {
    SimSession session = sessionWithResend();

    replayer().replay(session, new SplittableRandom(1));

    Set<String> chosen =
        session.events().stream()
            .filter(e -> Duplicates.chosen(e.eventId()))
            .map(e -> e.eventId().toString())
            .collect(Collectors.toSet());
    List<String> singles =
        collector.bodies.stream()
            .map(b -> MAPPER.readTree(b).get("events"))
            .filter(
                events ->
                    events.size() == 1 && chosen.contains(events.get(0).get("event_id").asString()))
            .map(events -> events.get(0).get("event_id").asString())
            .toList();
    assertThat(singles).containsAll(chosen);
    assertThat(Timestamps.format(START)).isNotBlank();
  }

  @Test
  void stop_ends_replay_after_current_wait_and_skips_resends() {
    CollectorClient client =
        new CollectorClient(collector.uri(), 4, 2, Duration.ofSeconds(5), stats, millis -> {});
    SessionReplayer[] holder = new SessionReplayer[1];
    holder[0] =
        new SessionReplayer(
            new CollectEnvelopeWriter(MAPPER),
            client,
            clock,
            millis -> {
              clock.sleeper().sleep(millis);
              holder[0].stop();
            },
            Runnable::run);
    SimSession session = sessionWithRequests(3);

    List<SimEvent> delivered = holder[0].replay(session, new SplittableRandom(1));

    assertThat(collector.bodies).hasSize(1);
    assertThat(delivered).isNotEmpty().hasSizeLessThan(session.events().size());
  }

  @Test
  void warm_replay_skips_requests_already_in_the_past() {
    // 预热：会话在过去某一刻开始，只发还没到期的请求，已过去的不补发
    SimSession session = null;
    for (int seed = 1; seed < 2_000 && session == null; seed++) {
      SimSession candidate =
          GENERATOR.human(
              POPULATION.person(seed), START.minusSeconds(60), new SplittableRandom(seed));
      boolean before = candidate.events().stream().anyMatch(e -> e.sentAt().isBefore(START));
      boolean after = candidate.events().stream().anyMatch(e -> !e.sentAt().isBefore(START));
      if (before && after) {
        session = candidate;
      }
    }

    List<SimEvent> delivered = replayer().replayWarm(session, new SplittableRandom(1));

    assertThat(delivered).isNotEmpty().allMatch(e -> !e.sentAt().isBefore(START));
    assertThat(delivered)
        .hasSize((int) session.events().stream().filter(e -> !e.sentAt().isBefore(START)).count());
  }

  private static SimSession sessionWithRequests(int atLeast) {
    for (int seed = 1; seed < 2_000; seed++) {
      SimSession session = session(seed);
      long requests =
          session.events().stream()
              .map(e -> e.sentAt() + "#" + e.name().source())
              .distinct()
              .count();
      if (requests >= atLeast) {
        return session;
      }
    }
    throw new IllegalStateException("找不到足够多请求的会话");
  }

  private static int resent(SimSession session) {
    return (int) session.events().stream().filter(e -> Duplicates.chosen(e.eventId())).count();
  }

  private static SimSession sessionWithResend() {
    for (int seed = 0; seed < 5_000; seed++) {
      SimSession session =
          GENERATOR.human(POPULATION.person(seed % 2_000), START, new SplittableRandom(seed));
      if (resent(session) > 0) {
        return session;
      }
    }
    throw new IllegalStateException("找不到带重发事件的会话");
  }
}
