package dev.patra.analytics.infra.history.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.history.model.vo.HistoryCursor;
import dev.patra.analytics.domain.history.model.vo.ReadingEntry;
import dev.patra.analytics.domain.history.port.read.ReadingHistoryReadPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 翻页：最近打开的在前、同一时间文献 ID 大的在前；游标连续翻不重不漏；只看自己的、不看过期线之前的。
class ReadingHistoryReadAdapterIT extends HistoryPersistenceITSupport {

  private static final Instant T0 = Instant.parse("2026-11-02T08:00:00.123Z");
  private static final Instant SINCE = Instant.parse("2025-11-02T08:00:00Z");

  @Autowired private ReadingHistoryReadPort reads;

  @Test
  void pages_newest_first_without_gaps() {
    for (long article = 1; article <= 65; article++) {
      insertHistory(1L, article, T0.plusSeconds(article), article % 2 == 0);
    }

    List<List<ReadingEntry>> pages = pageThrough(1L, 30);

    assertThat(pages).extracting(List::size).containsExactly(30, 30, 5, 0);
    assertThat(pages.getFirst().getFirst())
        .isEqualTo(ReadingEntry.of(65L, T0.plusSeconds(65), false));
    assertThat(pages.stream().flatMap(List::stream).map(ReadingEntry::articleId))
        .containsExactlyElementsOf(LongStream.rangeClosed(1, 65).map(i -> 66 - i).boxed().toList());
  }

  @Test
  void same_millisecond_rows_page_stably() {
    for (long article = 1; article <= 40; article++) {
      insertHistory(1L, article, T0, false);
    }

    List<List<ReadingEntry>> pages = pageThrough(1L, 30);

    assertThat(pages).extracting(List::size).containsExactly(30, 10, 0);
    assertThat(pages.stream().flatMap(List::stream).map(ReadingEntry::articleId))
        .containsExactlyElementsOf(LongStream.rangeClosed(1, 40).map(i -> 41 - i).boxed().toList());
  }

  @Test
  void rows_older_than_since_are_hidden() {
    insertHistory(1L, 100L, SINCE.minusMillis(1), false);
    insertHistory(1L, 101L, SINCE, false);

    assertThat(reads.findPage(1L, SINCE, null, 30))
        .extracting(ReadingEntry::articleId)
        .containsExactly(101L);
  }

  @Test
  void other_users_rows_are_invisible() {
    insertHistory(1L, 100L, T0, false);
    insertHistory(2L, 101L, T0, false);

    assertThat(reads.findPage(1L, SINCE, null, 30))
        .extracting(ReadingEntry::articleId)
        .containsExactly(100L);
  }

  private List<List<ReadingEntry>> pageThrough(long userId, int limit) {
    List<List<ReadingEntry>> pages = new ArrayList<>();
    HistoryCursor after = null;
    while (true) {
      List<ReadingEntry> page = reads.findPage(userId, SINCE, after, limit);
      pages.add(page);
      if (page.isEmpty()) {
        return pages;
      }
      after = HistoryCursor.decode(page.getLast().cursor().encode());
    }
  }
}
