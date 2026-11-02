package dev.patra.analytics.domain.collect.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.collect.model.vo.CollectedEnvelope;
import dev.patra.analytics.domain.kernel.model.vo.EventName;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ArticleReadTest {

  private static final Instant READ_AT = Instant.parse("2026-11-01T08:30:10.177Z");

  @Test
  void article_viewed_by_user_is_a_plain_read() {
    assertThat(
            ArticleRead.from(
                event(EventName.ARTICLE_VIEWED, "884213907612315648", false, 39871245L), READ_AT))
        .contains(ArticleRead.of(884213907612315648L, 39871245L, READ_AT, false));
  }

  @Test
  void fulltext_and_pdf_are_fulltext_reads() {
    assertThat(ArticleRead.from(event(EventName.FULLTEXT_READ, "7", false, 9L), READ_AT))
        .contains(ArticleRead.of(7L, 9L, READ_AT, true));
    assertThat(ArticleRead.from(event(EventName.PDF_DOWNLOADED, "7", false, 9L), READ_AT))
        .contains(ArticleRead.of(7L, 9L, READ_AT, true));
  }

  @Test
  void anonymous_synthetic_or_other_events_are_not_reads() {
    assertThat(ArticleRead.from(event(EventName.ARTICLE_VIEWED, null, false, 9L), READ_AT))
        .isEmpty();
    assertThat(ArticleRead.from(event(EventName.ARTICLE_VIEWED, "7", true, 9L), READ_AT)).isEmpty();
    assertThat(ArticleRead.from(event(EventName.ARTICLE_COLLECTED, "7", false, 9L), READ_AT))
        .isEmpty();
    assertThat(ArticleRead.from(event(EventName.ARTICLE_VIEWED, "7", false, null), READ_AT))
        .isEmpty();
  }

  @Test
  void oversized_ids_are_not_recorded() {
    assertThat(
            ArticleRead.from(
                event(EventName.ARTICLE_VIEWED, "9999999999999999999", false, 9L), READ_AT))
        .isEmpty();
  }

  @Test
  void read_at_is_truncated_to_millis() {
    Instant micros = Instant.parse("2026-11-01T08:30:10.177456Z");

    assertThat(ArticleRead.from(event(EventName.ARTICLE_VIEWED, "7", false, 9L), micros))
        .map(ArticleRead::readAt)
        .contains(Instant.parse("2026-11-01T08:30:10.177Z"));
  }

  private static CollectedEnvelope event(
      EventName name, String userId, boolean synthetic, Long articleId) {
    return CollectedEnvelope.builder()
        .eventId("0193e1a2-7c4b-7d2e-9f10-000000000001")
        .eventName(name)
        .schemaVersion("1-0-0")
        .source(name.source())
        .createdAt(Instant.parse("2026-11-01T08:30:10.120Z"))
        .sentAt(Instant.parse("2026-11-01T08:30:12.345Z"))
        .deviceId("d1")
        .sessionId("s1")
        .userId(userId)
        .pagePath("/papers/9")
        .pageType("article")
        .clientIp("203.0.113.24")
        .userAgent("Mozilla/5.0")
        .synthetic(synthetic)
        .propertiesJson("{}")
        .articleId(articleId)
        .build();
  }
}
