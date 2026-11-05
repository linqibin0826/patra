package dev.patra.analytics.domain.insight.port.read;

import java.util.List;

/// 读共现快照。
public interface CoReadingReadPort {

  /// 一篇文献的前几篇一起读的文献。
  ///
  /// @param articleId 文献 ID
  /// @param limit 最多几篇
  /// @return 文献 ID，按名次
  List<Long> relatedOf(long articleId, int limit);
}
