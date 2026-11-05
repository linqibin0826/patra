package dev.patra.analytics.domain.insight.port.read;

import dev.patra.analytics.domain.insight.model.vo.HotReadingSummary;
import java.util.Collection;
import java.util.Set;

/// 读热读快照。
public interface HotReadingReadPort {

  /// 前几行与榜上总数。
  ///
  /// @param limit 最多几行
  /// @return 热读页；从没算过为 [HotReadingSummary#empty()]
  HotReadingSummary summary(int limit);

  /// 这批文献里哪些在榜上（补位的不算）。
  ///
  /// @param ids 文献 ID
  /// @return 在榜上的
  Set<Long> rankedAmong(Collection<Long> ids);
}
