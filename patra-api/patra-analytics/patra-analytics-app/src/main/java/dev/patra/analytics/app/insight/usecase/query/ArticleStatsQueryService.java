package dev.patra.analytics.app.insight.usecase.query;

import dev.patra.analytics.domain.insight.model.vo.ReaderStats;
import dev.patra.analytics.domain.insight.port.read.HotReadingReadPort;
import dev.patra.analytics.domain.insight.port.stats.ReaderStatsPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 批量计数（insight spec §4）：读者数来自 ClickHouse（带 60 秒缓存），是否上榜来自 PG 热读快照。不开事务。
@Service
@RequiredArgsConstructor
public class ArticleStatsQueryService {

  private final ReaderStatsPort stats;
  private final HotReadingReadPort hotReadings;

  /// 去重后按请求顺序每篇一行；没人看过的两个数都是 0。
  ///
  /// @param articleIds 文献 ID（调用方已校验 1 ~ 100 个正整数）
  /// @return 每篇一行
  public List<ArticleStatsView> stats(List<Long> articleIds) {
    List<Long> ids = List.copyOf(new LinkedHashSet<>(articleIds));
    Map<Long, ReaderStats> found = stats.statsOf(ids);
    Set<Long> hot = hotReadings.rankedAmong(ids);
    return ids.stream()
        .map(
            id -> {
              ReaderStats s = found.getOrDefault(id, ReaderStats.none(id));
              return ArticleStatsView.of(id, s.viewers(), s.fulltextReaders(), hot.contains(id));
            })
        .toList();
  }
}
