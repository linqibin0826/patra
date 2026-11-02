package dev.patra.analytics.domain.insight.port.signal;

import dev.patra.analytics.domain.insight.model.vo.CoReadingPair;
import dev.patra.analytics.domain.insight.model.vo.HotReadingCandidate;
import java.time.LocalDate;
import java.util.List;

/// 从事件明细算排名信号（ClickHouse `events`，只数真人会话）。
public interface ReadingSignalPort {

  /// 满足上榜门槛的文献，按热读规则的名次。
  ///
  /// @param today 北京时间的今天
  /// @param limit 最多几篇
  /// @return 候选，按名次
  /// @throws dev.patra.analytics.domain.insight.exception.InsightUnavailableException ClickHouse
  // 不可用
  List<HotReadingCandidate> hotCandidates(LocalDate today, int limit);

  /// 满足门槛的共现配对，每篇 X 按相似度取前几名，两个方向都有。
  ///
  /// @param today 北京时间的今天
  /// @param perArticle 每篇 X 最多几篇
  /// @return 配对，按 X 分组、组内按名次
  /// @throws dev.patra.analytics.domain.insight.exception.InsightUnavailableException ClickHouse
  // 不可用
  List<CoReadingPair> coReadingPairs(LocalDate today, int perArticle);
}
