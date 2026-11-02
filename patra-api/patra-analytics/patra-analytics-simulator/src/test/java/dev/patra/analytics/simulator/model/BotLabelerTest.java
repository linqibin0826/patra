package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.PageType;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SimEventName;
import dev.patra.analytics.simulator.event.SyntheticIds;
import dev.patra.analytics.simulator.event.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BotLabelerTest {

  private static final TrafficModel MODEL = TrafficModel.load(null, JsonMapper.builder().build());
  private static final ArticlePool POOL = ArticlePool.load(ArticlePoolTest.smallPool(), 200_000);
  private static final Population POPULATION =
      Population.build(
          MODEL.toBuilder().devices(2_000).registeredUsers(200).build(), new SyntheticIds(1), 7);
  private static final CrawlerGenerator CRAWLERS = new CrawlerGenerator(MODEL, POOL);
  private static final SessionGenerator HUMANS = new SessionGenerator(MODEL, POOL);
  private static final Instant START = Instant.parse("2026-11-01T02:00:00Z");

  @Test
  void declared_crawler_labeled_ua_list_everywhere() {
    SplittableRandom random = new SplittableRandom(1);
    SimSession labeled =
        BotLabeler.label(
            CRAWLERS.declared(POPULATION.freshDevice(9_000, true, random), START, random));

    assertThat(labeled.events()).allMatch(e -> "ua_list".equals(e.botReason()));
  }

  @Test
  void stealth_crawler_crosses_threshold_and_labels_all_events_after() {
    SplittableRandom random = new SplittableRandom(2);
    SimSession labeled =
        BotLabeler.label(
            CRAWLERS.stealth(POPULATION.freshDevice(9_001, false, random), START, random));

    int views = 0;
    boolean crossed = false;
    for (SimEvent event :
        labeled.events().stream().sorted(Comparator.comparing(SimEvent::sentAt)).toList()) {
      if (event.name() == SimEventName.ARTICLE_VIEWED) {
        views++;
        crossed = views > 30;
      }
      assertThat(event.botReason())
          .as("第 %d 篇之后的 %s", views, event.name())
          .isEqualTo(crossed ? "rate" : null);
    }
    assertThat(crossed).isTrue();
  }

  @Test
  void window_drops_back_below_threshold_after_five_minutes() {
    Person person = POPULATION.freshDevice(9_002, false, new SplittableRandom(3));
    List<SimEvent> events = new ArrayList<>();
    for (int i = 0; i < 31; i++) {
      events.add(view(person, START.plusSeconds(i)));
    }
    events.add(view(person, START.plusSeconds(30 + 301)));

    List<SimEvent> labeled =
        BotLabeler.label(
                new SimSession(
                    events.getFirst().sessionId(), person, SessionKind.STEALTH_CRAWLER, events))
            .events();

    assertThat(labeled.get(29).botReason()).isNull();
    assertThat(labeled.get(30).botReason()).isEqualTo("rate");
    assertThat(labeled.get(31).botReason()).isNull();
  }

  @Test
  void resent_event_id_does_not_raise_window() {
    Person person = POPULATION.freshDevice(9_003, false, new SplittableRandom(4));
    List<SimEvent> events = new ArrayList<>();
    SimEvent first = view(person, START);
    for (int i = 0; i < 40; i++) {
      events.add(first.toBuilder().sentAt(START.plusMillis(i)).createdAt(START).build());
    }

    assertThat(
            BotLabeler.label(new SimSession(first.sessionId(), person, SessionKind.HUMAN, events))
                .events())
        .allMatch(e -> e.botReason() == null);
  }

  @Test
  void human_sessions_never_labeled() {
    SplittableRandom random = new SplittableRandom(5);
    for (int i = 0; i < 2_000; i++) {
      SimSession labeled =
          BotLabeler.label(HUMANS.human(POPULATION.sample(random), START.plusSeconds(i), random));
      assertThat(labeled.events()).allMatch(e -> e.botReason() == null);
    }
  }

  private static SimEvent view(Person person, Instant at) {
    SplittableRandom random = new SplittableRandom(at.toEpochMilli());
    return SimEvent.builder()
        .eventId(UuidV7.of(at, random))
        .name(SimEventName.ARTICLE_VIEWED)
        .createdAt(at)
        .sentAt(at)
        .person(person)
        .sessionId(UuidV7.of(START, new SplittableRandom(0)))
        .pagePath("/papers/1")
        .pageType(PageType.ARTICLE)
        .properties(Map.of("article_id", "1"))
        .articleIndex(0)
        .build();
  }
}
