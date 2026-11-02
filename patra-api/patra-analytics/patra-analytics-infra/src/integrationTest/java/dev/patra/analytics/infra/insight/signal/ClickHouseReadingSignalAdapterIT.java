package dev.patra.analytics.infra.insight.signal;

import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.migratedDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.domain.insight.model.vo.CoReadingPair;
import dev.patra.analytics.domain.insight.model.vo.HotReadingCandidate;
import dev.patra.analytics.infra.ingest.support.ClickHouseITFixture;
import dev.patra.analytics.infra.insight.config.InsightProperties;
import dev.patra.analytics.infra.insight.stats.ClickHouseReaderStatsAdapter;
import dev.patra.analytics.infra.insight.support.InsightEvents;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.clickhouse.ClickHouseContainer;
import tools.jackson.databind.node.ObjectNode;

class ClickHouseReadingSignalAdapterIT {

  private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
  private static final LocalDate T = LocalDate.of(2026, 11, 2);

  private final Client client = migratedDatabase();
  private final ClickHouseReadingSignalAdapter adapter =
      new ClickHouseReadingSignalAdapter(client, InsightProperties.of(false, 8));

  @AfterEach
  void close() {
    client.close();
  }

  // ===== 热读 =====

  @Test
  void three_readers_put_article_on_board() {
    readers(InsightEvents.batch(), 100L, T, 1, 2, 3).insertInto(client);

    assertThat(hot(100L)).hasValueSatisfying(c -> assertThat(c.readers7d()).isEqualTo(3));
  }

  @Test
  void two_readers_do_not() {
    readers(InsightEvents.batch(), 100L, T, 1, 2).insertInto(client);

    assertThat(hot(100L)).isEmpty();
  }

  @Test
  void fulltext_weight_and_half_life() {
    InsightEvents events = readers(InsightEvents.batch(), 100L, T, 1, 2, 3);
    for (long person = 11; person <= 14; person++) {
      events.read(person, "fulltext_read", 200L, bj(T.minusDays(6), 10));
    }
    events.insertInto(client);

    assertThat(hot(100L).orElseThrow().score()).isCloseTo(3.0, within(1e-9));
    assertThat(hot(200L).orElseThrow().score()).isCloseTo(3.0, within(1e-9));
  }

  @Test
  void same_person_same_day_counts_once() {
    readers(InsightEvents.batch(), 100L, T, 1, 2, 3)
        .read(1, "article_viewed", 100L, bj(T, 11))
        .read(1, "article_viewed", 100L, bj(T, 12))
        .insertInto(client);

    assertThat(hot(100L).orElseThrow().score()).isCloseTo(3.0, within(1e-9));
  }

  @Test
  void reads_outside_14_days_are_ignored() {
    InsightEvents events = readers(InsightEvents.batch(), 100L, T, 1, 2, 3);
    readers(events, 100L, T.minusDays(14), 4, 5, 6).insertInto(client);

    assertThat(hot(100L).orElseThrow().score()).isCloseTo(3.0, within(1e-9));
  }

  @Test
  void eligibility_uses_last_7_days() {
    readers(InsightEvents.batch(), 100L, T.minusDays(7), 1, 2, 3).insertInto(client);

    assertThat(hot(100L)).isEmpty();
  }

  @Test
  void days_are_cut_at_beijing_midnight() {
    readers(InsightEvents.batch(), 100L, T, 2, 3)
        .read(1, "article_viewed", 100L, bj(T.minusDays(1), LocalTime.of(23, 59)))
        .read(1, "article_viewed", 100L, bj(T, LocalTime.of(0, 1)))
        .insertInto(client);

    assertThat(hot(100L).orElseThrow().score())
        .isCloseTo(3.0 + Math.pow(0.5, 1.0 / 3), within(1e-9));
  }

  @Test
  void hot_ignores_server_only_bot_and_synthetic_sessions() {
    String botSession = UUID.randomUUID().toString();
    readers(InsightEvents.batch(), 100L, T, 1, 2)
        .session(
            3, UUID.randomUUID().toString(), false, false, false, "article_viewed", 100L, bj(T, 9))
        .session(4, botSession, false, false, true, "article_viewed", 100L, bj(T, 9))
        .session(4, botSession, true, false, false, "article_viewed", 9L, bj(T, 9))
        .session(
            5, UUID.randomUUID().toString(), false, true, true, "article_viewed", 100L, bj(T, 9))
        .insertInto(client);

    assertThat(hot(100L)).isEmpty();
    ClickHouseReadingSignalAdapter withSynthetic =
        new ClickHouseReadingSignalAdapter(client, InsightProperties.of(true, 8));
    assertThat(withSynthetic.hotCandidates(T, 150))
        .singleElement()
        .satisfies(c -> assertThat(c.readers7d()).isEqualTo(3));
  }

  @Test
  void hot_orders_by_score_then_readers_then_id() {
    InsightEvents events = readers(InsightEvents.batch(), 300L, T, 1, 2, 3, 4);
    readers(events, 200L, T, 1, 2, 3);
    readers(events, 100L, T, 1, 2, 3).insertInto(client);

    assertThat(adapter.hotCandidates(T, 150))
        .extracting(HotReadingCandidate::articleId)
        .containsExactly(300L, 100L, 200L);
    assertThat(adapter.hotCandidates(T, 1)).hasSize(1);
  }

  @Test
  void hot_readers_match_reader_stats() {
    InsightEvents events = readers(InsightEvents.batch(), 100L, T, 1, 2, 3, 4);
    events.read(1, "fulltext_read", 100L, bj(T.minusDays(3), 10));
    readers(events, 100L, T.minusDays(2), 5).insertInto(client);

    long viewers =
        new ClickHouseReaderStatsAdapter(client, InsightProperties.of(false, 8))
            .statsOf(List.of(100L))
            .get(100L)
            .viewers();

    assertThat(hot(100L).orElseThrow().readers7d()).isEqualTo((int) viewers).isEqualTo(5);
  }

  // ===== 共现 =====

  @Test
  void three_people_same_day_make_a_pair() {
    InsightEvents events = InsightEvents.batch();
    readers(events, 1L, T, 1, 2, 3);
    readers(events, 2L, T, 1, 2, 3).insertInto(client);

    Map<Long, List<CoReadingPair>> pairs = byArticle(adapter.coReadingPairs(T, 25));

    assertThat(pairs.get(1L))
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.relatedArticleId()).isEqualTo(2L);
              assertThat(p.coReaders()).isEqualTo(3);
              assertThat(p.similarity()).isCloseTo(1.0, within(1e-9));
            });
    assertThat(pairs.get(2L))
        .singleElement()
        .satisfies(p -> assertThat(p.relatedArticleId()).isEqualTo(1L));
  }

  @Test
  void two_people_do_not() {
    InsightEvents events = InsightEvents.batch();
    readers(events, 1L, T, 1, 2);
    readers(events, 2L, T, 1, 2).insertInto(client);

    assertThat(adapter.coReadingPairs(T, 25)).isEmpty();
  }

  @Test
  void same_people_different_days_do_not() {
    InsightEvents events = InsightEvents.batch();
    readers(events, 1L, T, 1, 2, 3);
    readers(events, 2L, T.minusDays(1), 1, 2, 3).insertInto(client);

    assertThat(adapter.coReadingPairs(T, 25)).isEmpty();
  }

  @Test
  void co_reads_outside_14_days_are_ignored() {
    InsightEvents events = InsightEvents.batch();
    readers(events, 1L, T.minusDays(14), 1, 2, 3);
    readers(events, 2L, T.minusDays(14), 1, 2, 3).insertInto(client);

    assertThat(adapter.coReadingPairs(T, 25)).isEmpty();
  }

  @Test
  void basket_keeps_first_20_reads_of_the_day() {
    InsightEvents events = InsightEvents.batch();
    for (long person = 1; person <= 3; person++) {
      for (int article = 1; article <= 25; article++) {
        events.read(person, "article_viewed", article, bj(T, 8).plusSeconds(60L * article));
      }
    }
    events.insertInto(client);

    List<CoReadingPair> pairs = adapter.coReadingPairs(T, 25);

    assertThat(pairs).hasSize(20 * 19);
    assertThat(pairs)
        .allSatisfy(
            p -> {
              assertThat(p.articleId()).isLessThanOrEqualTo(20L);
              assertThat(p.relatedArticleId()).isLessThanOrEqualTo(20L);
            });
  }

  @Test
  void cosine_demotes_popular_article() {
    cosineFixture().insertInto(client);

    assertThat(byArticle(adapter.coReadingPairs(T, 25)).get(1L))
        .extracting(CoReadingPair::relatedArticleId)
        .containsExactly(2L, 3L);
  }

  @Test
  void co_reading_ignores_server_only_bot_and_synthetic_sessions() {
    String botSession = UUID.randomUUID().toString();
    InsightEvents events = InsightEvents.batch();
    readers(events, 1L, T, 1, 2);
    readers(events, 2L, T, 1, 2);
    String serverOnly = UUID.randomUUID().toString();
    events
        .session(3, serverOnly, false, false, false, "article_viewed", 1L, bj(T, 9))
        .session(3, serverOnly, false, false, false, "article_viewed", 2L, bj(T, 9))
        .session(4, botSession, false, false, true, "article_viewed", 1L, bj(T, 9))
        .session(4, botSession, true, false, false, "article_viewed", 2L, bj(T, 9));
    String synthetic = UUID.randomUUID().toString();
    events
        .session(5, synthetic, false, true, true, "article_viewed", 1L, bj(T, 9))
        .session(5, synthetic, false, true, false, "article_viewed", 2L, bj(T, 9))
        .insertInto(client);

    assertThat(adapter.coReadingPairs(T, 25)).isEmpty();
    assertThat(
            new ClickHouseReadingSignalAdapter(client, InsightProperties.of(true, 8))
                .coReadingPairs(T, 25))
        .hasSize(2);
  }

  @Test
  void pairs_are_complete_across_shards() {
    cosineFixture().insertInto(client);

    List<CoReadingPair> oneShard =
        new ClickHouseReadingSignalAdapter(client, InsightProperties.of(false, 1))
            .coReadingPairs(T, 25);

    assertThat(
            new ClickHouseReadingSignalAdapter(client, InsightProperties.of(false, 3))
                .coReadingPairs(T, 25))
        .containsExactlyElementsOf(oneShard)
        .isNotEmpty();
  }

  // ===== 规模 =====

  /// 只看会话特征表里「窗口内有阅读」的会话：没有阅读的会话（合成流量里的首页、检索会话）再多，
  /// 也不进真人会话的分组。用 `max_rows_to_group_by` 让分组行数超标时直接报错。
  @Test
  void real_session_grouping_only_covers_sessions_with_reads() {
    InsightEvents events = readers(InsightEvents.batch(), 1L, T, 1, 2, 3);
    readers(events, 2L, T, 1, 2, 3);
    for (int i = 0; i < 200; i++) {
      ObjectNode view =
          ClickHouseITFixture.pageViewIn(
              ClickHouseITFixture.event(
                  node -> node.put("session_id", UUID.randomUUID().toString())));
      view.put("derived_at", bj(T, 9).toString());
      events.raw(view);
    }
    events.insertInto(client);

    try (Client capped = cappedGroupBy(50)) {
      ClickHouseReadingSignalAdapter adapter =
          new ClickHouseReadingSignalAdapter(capped, InsightProperties.of(false, 8));

      assertThat(adapter.hotCandidates(T, 150)).hasSize(2);
      assertThat(adapter.coReadingPairs(T, 25)).hasSize(2);
    }
  }

  // ===== fixtures =====

  /// X(1) 4 个读者；Y(2) 4 个读者，其中 3 人同日也读了 X；Z(3) 30 个读者，其中 4 人同日也读了 X。
  private static InsightEvents cosineFixture() {
    InsightEvents events = InsightEvents.batch();
    readers(events, 1L, T, 1, 2, 3, 4);
    readers(events, 2L, T, 1, 2, 3, 5);
    for (long person = 1; person <= 30; person++) {
      events.read(person, "article_viewed", 3L, bj(T, 10));
    }
    return events;
  }

  /// 这些人各自在一个真人会话里于某天 10:00（北京时间）看了这篇。
  private static InsightEvents readers(
      InsightEvents events, long articleId, LocalDate day, long... persons) {
    for (long person : persons) {
      events.read(person, "article_viewed", articleId, bj(day, 10));
    }
    return events;
  }

  private Optional<HotReadingCandidate> hot(long articleId) {
    return adapter.hotCandidates(T, 150).stream()
        .filter(c -> c.articleId() == articleId)
        .findFirst();
  }

  private static Map<Long, List<CoReadingPair>> byArticle(List<CoReadingPair> pairs) {
    return pairs.stream().collect(Collectors.groupingBy(CoReadingPair::articleId));
  }

  private static Instant bj(LocalDate day, int hour) {
    return bj(day, LocalTime.of(hour, 0));
  }

  private static Instant bj(LocalDate day, LocalTime time) {
    return day.atTime(time).atZone(BEIJING).toInstant();
  }

  /// 连同一个库、但每条查询的分组行数上限为 `rows` 的客户端。
  private Client cappedGroupBy(int rows) {
    ClickHouseContainer container = ClickHouseContainerInitializer.getClickHouseContainer();
    return new Client.Builder()
        .addEndpoint("http://" + container.getHost() + ":" + container.getMappedPort(8123))
        .setUsername(container.getUsername())
        .setPassword(container.getPassword())
        .setDefaultDatabase(client.getDefaultDatabase())
        .serverSetting("max_rows_to_group_by", Integer.toString(rows))
        .serverSetting("group_by_overflow_mode", "throw")
        .build();
  }
}
