package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.EventSource;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SimEventName;
import dev.patra.analytics.simulator.event.SyntheticIds;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SessionGeneratorTest {

  private static final TrafficModel MODEL = TrafficModel.load(null, JsonMapper.builder().build());
  private static final ArticlePool POOL = ArticlePool.load(ArticlePoolTest.smallPool(), 200_000);
  private static final Population POPULATION =
      Population.build(
          MODEL.toBuilder().devices(20_000).registeredUsers(2_000).build(), new SyntheticIds(1), 7);
  private static final SessionGenerator GENERATOR = new SessionGenerator(MODEL, POOL);
  private static final List<SimSession> SESSIONS = humanSessions(5_000);

  static List<SimSession> humanSessions(int count) {
    SplittableRandom random = new SplittableRandom(21);
    Instant start = Instant.parse("2026-11-01T02:00:00Z");
    List<SimSession> sessions = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      sessions.add(GENERATOR.human(POPULATION.sample(random), start.plusSeconds(i * 7L), random));
    }
    return sessions;
  }

  @Test
  void every_human_session_has_a_client_event() {
    for (SimSession session : SESSIONS) {
      assertThat(session.kind()).isEqualTo(SessionKind.HUMAN);
      assertThat(session.events()).anyMatch(e -> e.name().source() == EventSource.CLIENT);
    }
  }

  @Test
  void fulltext_and_pdf_follow_article_view_of_same_article_in_same_session() {
    for (SimSession session : SESSIONS) {
      Set<Integer> viewed = new HashSet<>();
      for (SimEvent event : session.events()) {
        switch (event.name()) {
          case ARTICLE_VIEWED -> viewed.add(event.articleIndex());
          case FULLTEXT_READ, PDF_DOWNLOADED, ARTICLE_COLLECTED, CITATION_COPIED ->
              assertThat(viewed).contains(event.articleIndex());
          default -> {}
        }
      }
    }
  }

  @Test
  void fulltext_read_at_most_once_per_article_page() {
    for (SimSession session : SESSIONS) {
      int sinceLastView = 0;
      for (SimEvent event : session.events()) {
        if (event.name() == SimEventName.ARTICLE_VIEWED) {
          sinceLastView = 0;
        } else if (event.name() == SimEventName.FULLTEXT_READ) {
          sinceLastView++;
          assertThat(sinceLastView).isEqualTo(1);
          assertThat((Integer) event.properties().get("visible_seconds")).isBetween(10, 300);
        }
      }
    }
  }

  @Test
  void clicks_match_an_earlier_impression() {
    for (SimSession session : SESSIONS) {
      Set<String> impressed = new HashSet<>();
      for (SimEvent event : session.events()) {
        if (event.name() == SimEventName.ARTICLE_IMPRESSED) {
          impressed.add(cardKey(event));
        } else if (event.name() == SimEventName.ARTICLE_CLICKED) {
          assertThat(impressed).contains(cardKey(event));
        }
      }
    }
  }

  @Test
  void click_is_followed_by_view_of_same_article() {
    for (SimSession session : SESSIONS) {
      List<SimEvent> events = session.events();
      for (int i = 0; i < events.size(); i++) {
        if (events.get(i).name() == SimEventName.ARTICLE_CLICKED) {
          int clicked = events.get(i).articleIndex();
          assertThat(events.subList(i + 1, events.size()))
              .filteredOn(e -> e.name() == SimEventName.ARTICLE_VIEWED)
              .first()
              .extracting(SimEvent::articleIndex)
              .isEqualTo(clicked);
        }
      }
    }
  }

  @Test
  void collected_and_library_only_when_logged_in() {
    for (SimSession session : SESSIONS) {
      if (session.person().userId() == null) {
        assertThat(session.events())
            .noneMatch(
                e ->
                    e.name() == SimEventName.ARTICLE_COLLECTED
                        || e.name() == SimEventName.EXPORT_COMPLETED
                        || "/library".equals(e.pagePath()));
      }
    }
  }

  @Test
  void article_views_in_any_five_minutes_at_most_15() {
    for (SimSession session : SESSIONS) {
      Deque<Instant> window = new ArrayDeque<>();
      for (SimEvent event : session.events()) {
        if (event.name() != SimEventName.ARTICLE_VIEWED) {
          continue;
        }
        Instant now = event.sentAt();
        while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(Duration.ofMinutes(5)))) {
          window.pollFirst();
        }
        window.addLast(now);
        assertThat(window.size()).isLessThanOrEqualTo(15);
      }
    }
  }

  @Test
  void events_sorted_and_sent_after_created() {
    for (SimSession session : SESSIONS) {
      SimEvent previous = null;
      for (SimEvent event : session.events()) {
        assertThat(event.sentAt()).isAfterOrEqualTo(event.createdAt());
        assertThat(event.sessionId()).isEqualTo(session.sessionId());
        if (previous != null) {
          assertThat(event.createdAt()).isAfterOrEqualTo(previous.createdAt());
        }
        previous = event;
      }
    }
  }

  @Test
  void server_events_sent_within_50_millis_and_client_batches_within_5_seconds() {
    for (SimSession session : SESSIONS) {
      for (SimEvent event : session.events()) {
        Duration lag = Duration.between(event.createdAt(), event.sentAt());
        if (event.name().source() == EventSource.SERVER) {
          assertThat(lag).isBetween(Duration.ofMillis(5), Duration.ofMillis(50));
        } else {
          assertThat(lag).isLessThanOrEqualTo(Duration.ofSeconds(5));
        }
      }
    }
  }

  @Test
  void client_batches_hold_at_most_50_events() {
    for (SimSession session : SESSIONS) {
      Map<Instant, Long> perBatch =
          session.events().stream()
              .filter(e -> e.name().source() == EventSource.CLIENT)
              .collect(Collectors.groupingBy(SimEvent::sentAt, Collectors.counting()));
      assertThat(perBatch.values()).allMatch(n -> n <= 50);
    }
  }

  @Test
  void page_paths_match_page_types() {
    for (SimSession session : SESSIONS) {
      for (SimEvent event : session.events()) {
        switch (event.pageType()) {
          case HOME -> assertThat(event.pagePath()).isEqualTo("/");
          case SEARCH -> assertThat(event.pagePath()).isEqualTo("/papers");
          case ARTICLE -> assertThat(event.pagePath()).matches("^/papers/[1-9][0-9]*$");
          case LIBRARY -> assertThat(event.pagePath()).isEqualTo("/library");
        }
      }
    }
  }

  @Test
  void same_session_prefers_interest_topic() {
    int pairs = 0;
    int sameTopic = 0;
    for (SimSession session : SESSIONS) {
      List<Integer> viewed =
          session.events().stream()
              .filter(e -> e.name() == SimEventName.ARTICLE_VIEWED)
              .map(SimEvent::articleIndex)
              .toList();
      for (int i = 1; i < viewed.size(); i++) {
        pairs++;
        if (POOL.topicOf(viewed.get(i)) == POOL.topicOf(viewed.get(i - 1))) {
          sameTopic++;
        }
      }
    }
    SplittableRandom random = new SplittableRandom(3);
    int baseline = 0;
    for (int i = 0; i < 100_000; i++) {
      if (POOL.topicOf(POOL.sampleGlobal(random)) == POOL.topicOf(POOL.sampleGlobal(random))) {
        baseline++;
      }
    }

    assertThat(pairs).isGreaterThan(1_000);
    assertThat(sameTopic / (double) pairs).isGreaterThan(3 * baseline / 100_000.0);
  }

  @Test
  void zero_result_searches_have_no_impressions() {
    int zero = 0;
    for (SimSession session : SESSIONS) {
      List<SimEvent> events = session.events();
      for (SimEvent event : events) {
        if (event.name() == SimEventName.SEARCH_PERFORMED
            && Objects.equals(event.properties().get("result_count"), 0)) {
          zero++;
          String searchId = (String) event.properties().get("search_id");
          assertThat(events)
              .noneMatch(
                  e ->
                      e.name() == SimEventName.ARTICLE_IMPRESSED
                          && searchId.equals(e.properties().get("list_id")));
        }
      }
    }
    assertThat(zero).isPositive();
  }

  @Test
  void mean_events_per_human_session_is_between_14_and_22() {
    assertThat(GENERATOR.meanEventsPerHumanSession(POPULATION, 5)).isBetween(14.0, 22.0);
  }

  @Test
  void noscript_session_has_only_server_events_and_1_to_3_pages() {
    SplittableRandom random = new SplittableRandom(4);
    for (int i = 0; i < 1_000; i++) {
      SimSession session =
          GENERATOR.noscript(
              POPULATION.freshDevice(30_000 + i, false, random),
              Instant.parse("2026-11-01T02:00:00Z"),
              random);

      assertThat(session.kind()).isEqualTo(SessionKind.NOSCRIPT);
      assertThat(session.events()).allMatch(e -> e.name().source() == EventSource.SERVER);
      assertThat(session.events()).hasSizeBetween(1, 3);
    }
  }

  @Test
  void same_seed_same_session() {
    Person person = POPULATION.person(10);
    Instant start = Instant.parse("2026-11-01T02:00:00Z");

    assertThat(GENERATOR.human(person, start, new SplittableRandom(99)))
        .isEqualTo(GENERATOR.human(person, start, new SplittableRandom(99)));
  }

  private static String cardKey(SimEvent event) {
    return event.properties().get("list_id")
        + "#"
        + event.properties().get("position")
        + "#"
        + event.articleIndex();
  }
}
