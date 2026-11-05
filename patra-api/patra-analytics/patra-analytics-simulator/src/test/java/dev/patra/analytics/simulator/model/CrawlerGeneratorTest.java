package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.EventSource;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SimEventName;
import dev.patra.analytics.simulator.event.SyntheticIds;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CrawlerGeneratorTest {

  private static final TrafficModel MODEL = TrafficModel.load(null, JsonMapper.builder().build());
  private static final ArticlePool POOL = ArticlePool.load(ArticlePoolTest.smallPool(), 200_000);
  private static final Population POPULATION =
      Population.build(
          MODEL.toBuilder().devices(1_000).registeredUsers(100).build(), new SyntheticIds(1), 7);
  private static final CrawlerGenerator CRAWLERS = new CrawlerGenerator(MODEL, POOL);
  private static final Instant START = Instant.parse("2026-11-01T02:00:00Z");

  @Test
  void declared_crawler_walks_pool_linearly_with_server_events_only() {
    SplittableRandom random = new SplittableRandom(1);
    SimSession session =
        CRAWLERS.declared(POPULATION.freshDevice(5_000, true, random), START, random);

    assertThat(session.kind()).isEqualTo(SessionKind.DECLARED_CRAWLER);
    assertThat(session.events()).hasSizeBetween(50, 300);
    assertThat(session.events()).allMatch(e -> e.name() == SimEventName.ARTICLE_VIEWED);
    List<Integer> articles = session.events().stream().map(SimEvent::articleIndex).toList();
    for (int i = 1; i < articles.size(); i++) {
      assertThat(articles.get(i)).isEqualTo(POOL.linear(articles.get(i - 1) + 1));
    }
  }

  @Test
  void stealth_crawler_runs_scripts_and_reads_fast() {
    SplittableRandom random = new SplittableRandom(2);
    SimSession session =
        CRAWLERS.stealth(POPULATION.freshDevice(5_001, false, random), START, random);

    assertThat(session.kind()).isEqualTo(SessionKind.STEALTH_CRAWLER);
    assertThat(session.events()).anyMatch(e -> e.name().source() == EventSource.CLIENT);
    long views =
        session.events().stream().filter(e -> e.name() == SimEventName.ARTICLE_VIEWED).count();
    assertThat(views).isBetween(60L, 200L);
    for (SimEvent event : session.events()) {
      assertThat(event.sentAt()).isAfterOrEqualTo(event.createdAt());
    }
  }
}
