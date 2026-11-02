package dev.patra.analytics.simulator.truth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.PageType;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SimEventName;
import dev.patra.analytics.simulator.event.UuidV7;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.Person;
import dev.patra.analytics.simulator.model.Region;
import dev.patra.analytics.simulator.model.SessionKind;
import dev.patra.analytics.simulator.model.SimSession;
import dev.patra.analytics.simulator.model.UaProfile;
import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class TruthLedgerTest {

  static final ArticlePool POOL = ArticlePool.load(smallPool(), 200_000);
  private static final UaProfile CHROME =
      new UaProfile("Mozilla/5.0 Chrome", "Chrome", "Mac OS", "Desktop", false, 1);
  private static final Region BEIJING = new Region("CN", "北京市", List.of("123.125.81.6"), 1);
  private static final Person ALICE =
      new Person(1, "sim001aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 9L, CHROME, BEIJING);
  private static final Person BOB =
      new Person(2, "sim001bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", null, CHROME, BEIJING);
  private static final Instant NOON = Instant.parse("2026-11-01T04:00:00Z");
  private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

  private final TruthLedger ledger = new TruthLedger(POOL, SimEvent::createdAt);

  @Test
  void same_person_same_article_same_day_counts_once() {
    SimSession first = session(ALICE, SessionKind.HUMAN, view(ALICE, 3, NOON), page(ALICE, NOON));
    SimSession second =
        session(
            ALICE, SessionKind.HUMAN, view(ALICE, 3, NOON.plusSeconds(3_600)), page(ALICE, NOON));
    ledger.accept(first, first.events());
    ledger.accept(second, second.events());
    ledger.accept(first, first.events());

    assertThat(ledger.closeAll()).containsExactly(new TruthRow(DAY, POOL.idAt(3), 1, 0));
  }

  @Test
  void different_people_count_separately() {
    SimSession a = session(ALICE, SessionKind.HUMAN, view(ALICE, 3, NOON), page(ALICE, NOON));
    SimSession b = session(BOB, SessionKind.HUMAN, view(BOB, 3, NOON), page(BOB, NOON));
    ledger.accept(a, a.events());
    ledger.accept(b, b.events());

    assertThat(ledger.closeAll()).containsExactly(new TruthRow(DAY, POOL.idAt(3), 2, 0));
  }

  @Test
  void session_crossing_midnight_counts_on_both_days() {
    Instant beforeMidnight = Instant.parse("2026-11-01T15:59:00Z");
    Instant afterMidnight = Instant.parse("2026-11-01T16:01:00Z");
    SimSession session =
        session(
            ALICE,
            SessionKind.HUMAN,
            page(ALICE, beforeMidnight),
            view(ALICE, 3, beforeMidnight),
            view(ALICE, 3, afterMidnight));
    ledger.accept(session, session.events());

    assertThat(ledger.closeAll())
        .containsExactly(
            new TruthRow(DAY, POOL.idAt(3), 1, 0),
            new TruthRow(DAY.plusDays(1), POOL.idAt(3), 1, 0));
  }

  @Test
  void sessions_with_a_bot_labeled_event_do_not_count() {
    SimSession declared =
        session(
            BOB,
            SessionKind.DECLARED_CRAWLER,
            view(BOB, 3, NOON).withBotReason("ua_list"),
            page(BOB, NOON).withBotReason("ua_list"));
    SimSession stealth =
        session(
            BOB,
            SessionKind.STEALTH_CRAWLER,
            view(BOB, 3, NOON),
            page(BOB, NOON),
            view(BOB, 4, NOON.plusSeconds(90)).withBotReason("rate"));
    ledger.accept(declared, declared.events());
    ledger.accept(stealth, stealth.events());

    assertThat(ledger.closeAll()).isEmpty();
  }

  @Test
  void noscript_session_does_not_count() {
    SimSession session = session(BOB, SessionKind.NOSCRIPT, view(BOB, 3, NOON));
    ledger.accept(session, session.events());

    assertThat(ledger.closeAll()).isEmpty();
  }

  @Test
  void stealth_crawler_cut_before_threshold_counts_like_the_pipeline_would() {
    // spec §7.1：真值用同一套判定规则（看送达的事件有没有被判为爬虫），不用模拟器私下知道的身份；
    // 没跨过频率阈值就被截断的伪装爬虫，管线认不出，真值也照算
    SimSession session =
        session(BOB, SessionKind.STEALTH_CRAWLER, view(BOB, 3, NOON), page(BOB, NOON));
    ledger.accept(session, session.events());

    assertThat(ledger.closeAll()).containsExactly(new TruthRow(DAY, POOL.idAt(3), 1, 0));
  }

  @Test
  void human_session_without_acked_client_event_does_not_count() {
    SimEvent view = view(ALICE, 3, NOON);
    SimSession session = session(ALICE, SessionKind.HUMAN, view, page(ALICE, NOON));
    ledger.accept(session, List.of(view));

    assertThat(ledger.closeAll()).isEmpty();
  }

  @Test
  void unacked_events_do_not_count() {
    SimEvent page = page(ALICE, NOON);
    SimSession session = session(ALICE, SessionKind.HUMAN, view(ALICE, 3, NOON), page);
    ledger.accept(session, List.of(page));

    assertThat(ledger.closeAll()).isEmpty();
  }

  @Test
  void fulltext_column_counts_only_fulltext_and_pdf() {
    SimSession a =
        session(
            ALICE,
            SessionKind.HUMAN,
            page(ALICE, NOON),
            view(ALICE, 3, NOON),
            event(ALICE, SimEventName.FULLTEXT_READ, 3, NOON.plusSeconds(30)));
    SimSession b =
        session(
            BOB,
            SessionKind.HUMAN,
            page(BOB, NOON),
            view(BOB, 3, NOON),
            event(BOB, SimEventName.ARTICLE_IMPRESSED, 5, NOON),
            event(BOB, SimEventName.PDF_DOWNLOADED, 3, NOON.plusSeconds(30)));
    ledger.accept(a, a.events());
    ledger.accept(b, b.events());

    assertThat(ledger.closeAll()).containsExactly(new TruthRow(DAY, POOL.idAt(3), 2, 2));
  }

  @Test
  void close_days_before_releases_only_earlier_days() {
    SimSession first = session(ALICE, SessionKind.HUMAN, page(ALICE, NOON), view(ALICE, 3, NOON));
    Instant tomorrow = NOON.plusSeconds(86_400);
    SimSession second =
        session(ALICE, SessionKind.HUMAN, page(ALICE, tomorrow), view(ALICE, 4, tomorrow));
    ledger.accept(first, first.events());
    ledger.accept(second, second.events());

    assertThat(ledger.closeDaysBefore(DAY.plusDays(1)))
        .containsExactly(new TruthRow(DAY, POOL.idAt(3), 1, 0));
    assertThat(ledger.closeAll())
        .containsExactly(new TruthRow(DAY.plusDays(1), POOL.idAt(4), 1, 0));
  }

  static Path smallPool() {
    return SmallArticlePoolFixture.path();
  }

  private static SimSession session(Person person, SessionKind kind, SimEvent... events) {
    List<SimEvent> list = new ArrayList<>(List.of(events));
    return new SimSession(
        UuidV7.of(NOON, new SplittableRandom(person.index())), person, kind, list);
  }

  private static SimEvent view(Person person, int article, Instant at) {
    return event(person, SimEventName.ARTICLE_VIEWED, article, at);
  }

  private static SimEvent page(Person person, Instant at) {
    return event(person, SimEventName.PAGE_VIEWED, -1, at);
  }

  private static SimEvent event(Person person, SimEventName name, int article, Instant at) {
    return SimEvent.builder()
        .eventId(UuidV7.of(at, new SplittableRandom(at.toEpochMilli() + article)))
        .name(name)
        .createdAt(at)
        .sentAt(at)
        .person(person)
        .sessionId(UuidV7.of(NOON, new SplittableRandom(person.index())))
        .pagePath("/")
        .pageType(PageType.HOME)
        .properties(Map.of())
        .articleIndex(article)
        .build();
  }
}
