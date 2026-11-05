package dev.patra.analytics.app.insight.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.vo.ReaderStats;
import dev.patra.analytics.domain.insight.port.read.HotReadingReadPort;
import dev.patra.analytics.domain.insight.port.stats.ReaderStatsPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArticleStatsQueryServiceTest {

  private final ReaderStatsPort stats = mock(ReaderStatsPort.class);
  private final HotReadingReadPort hot = mock(HotReadingReadPort.class);
  private final ArticleStatsQueryService service = new ArticleStatsQueryService(stats, hot);

  @Test
  void dedupes_keeps_request_order_and_zero_fills() {
    when(stats.statsOf(List.of(3L, 1L, 2L))).thenReturn(Map.of(1L, ReaderStats.of(1L, 5, 2)));
    when(hot.rankedAmong(List.of(3L, 1L, 2L))).thenReturn(Set.of(3L));

    List<ArticleStatsView> views = service.stats(List.of(3L, 1L, 3L, 2L));

    assertThat(views)
        .containsExactly(
            ArticleStatsView.of(3L, 0, 0, true),
            ArticleStatsView.of(1L, 5, 2, false),
            ArticleStatsView.of(2L, 0, 0, false));
  }

  @Test
  void clickhouse_unavailable_propagates() {
    when(stats.statsOf(any())).thenThrow(new InsightUnavailableException("down", null));

    assertThatThrownBy(() -> service.stats(List.of(1L)))
        .isInstanceOf(InsightUnavailableException.class);
  }

  @Test
  void hot_lookup_uses_deduped_ids() {
    when(stats.statsOf(any())).thenReturn(Map.of());
    when(hot.rankedAmong(any())).thenReturn(Set.of());

    service.stats(List.of(1L, 1L));

    verify(hot).rankedAmong(List.of(1L));
  }
}
