package dev.patra.analytics.domain.insight.port.stats;

import dev.patra.analytics.domain.insight.model.vo.ReaderStats;
import java.util.Collection;
import java.util.Map;

/// 按文献批量取读者数（ClickHouse 汇总表，外面套 Redis 缓存）。
public interface ReaderStatsPort {

  /// 批量取读者数。
  ///
  /// @param articleIds 文献 ID，最多 100 个
  /// @return 有人看过的文献；没有的就是 0
  /// @throws dev.patra.analytics.domain.insight.exception.InsightUnavailableException ClickHouse
  // 不可用
  Map<Long, ReaderStats> statsOf(Collection<Long> articleIds);
}
