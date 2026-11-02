package dev.patra.analytics.infra.insight.stats;

import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.migratedDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.vo.ReaderStats;
import dev.patra.analytics.infra.ingest.config.ClickHouseClientConfiguration;
import dev.patra.analytics.infra.ingest.config.ClickHouseProperties;
import dev.patra.analytics.infra.insight.config.InsightProperties;
import dev.patra.analytics.infra.insight.support.InsightEvents;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClickHouseReaderStatsAdapterIT {

  private static final long X = 39871245L;
  private static final Instant AT = Instant.parse("2026-11-01T08:30:10Z");

  private final Client client = migratedDatabase();
  private final ClickHouseReaderStatsAdapter adapter =
      new ClickHouseReaderStatsAdapter(client, InsightProperties.of(false, 8));

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void same_person_counts_once() {
    InsightEvents.batch()
        .read(1, "article_viewed", X, AT)
        .read(1, "article_viewed", X, AT.plusSeconds(60))
        .read(1, "article_viewed", X, AT.plusSeconds(86_400))
        .insertInto(client);

    assertThat(adapter.statsOf(List.of(X))).containsEntry(X, ReaderStats.of(X, 1, 0));
  }

  @Test
  void fulltext_counts_in_both() {
    InsightEvents.batch()
        .read(1, "article_viewed", X, AT)
        .read(1, "fulltext_read", X, AT.plusSeconds(30))
        .read(2, "pdf_downloaded", X, AT)
        .read(3, "article_collected", X, AT)
        .insertInto(client);

    assertThat(adapter.statsOf(List.of(X))).containsEntry(X, ReaderStats.of(X, 3, 2));
  }

  @Test
  void server_only_session_is_not_counted() {
    InsightEvents.batch()
        .session(1, UUID.randomUUID().toString(), false, false, false, "article_viewed", X, AT)
        .insertInto(client);

    assertThat(adapter.statsOf(List.of(X))).doesNotContainKey(X);
  }

  @Test
  void bot_session_is_not_counted() {
    String session = UUID.randomUUID().toString();
    InsightEvents.batch()
        .session(1, session, false, false, true, "article_viewed", X, AT)
        .session(1, session, true, false, false, "article_viewed", 7L, AT.plusSeconds(5))
        .read(2, "article_viewed", X, AT)
        .insertInto(client);

    assertThat(adapter.statsOf(List.of(X))).containsEntry(X, ReaderStats.of(X, 1, 0));
  }

  @Test
  void synthetic_excluded_unless_enabled() {
    InsightEvents.batch()
        .session(1, UUID.randomUUID().toString(), false, true, true, "article_viewed", X, AT)
        .read(2, "article_viewed", X, AT)
        .insertInto(client);

    ClickHouseReaderStatsAdapter withSynthetic =
        new ClickHouseReaderStatsAdapter(client, InsightProperties.of(true, 8));

    assertThat(adapter.statsOf(List.of(X))).containsEntry(X, ReaderStats.of(X, 1, 0));
    assertThat(withSynthetic.statsOf(List.of(X))).containsEntry(X, ReaderStats.of(X, 2, 0));
  }

  @Test
  void several_articles_in_one_query_and_unknown_are_absent() {
    InsightEvents.batch()
        .read(1, "article_viewed", X, AT)
        .read(1, "article_viewed", 8L, AT)
        .read(2, "article_viewed", 8L, AT)
        .insertInto(client);

    assertThat(adapter.statsOf(List.of(X, 8L, 999L)))
        .containsOnlyKeys(X, 8L)
        .containsEntry(8L, ReaderStats.of(8L, 2, 0));
    assertThat(adapter.statsOf(List.of())).isEmpty();
  }

  @Test
  void clickhouse_down_throws_unavailable() {
    try (Client unreachable =
        new ClickHouseClientConfiguration()
            .clickHouseClient(
                new ClickHouseProperties("http://127.0.0.1:1", "default", "", "default"))) {
      ClickHouseReaderStatsAdapter down =
          new ClickHouseReaderStatsAdapter(unreachable, InsightProperties.of(false, 8));

      assertThatThrownBy(() -> down.statsOf(List.of(X)))
          .isInstanceOf(InsightUnavailableException.class);
    }
  }
}
