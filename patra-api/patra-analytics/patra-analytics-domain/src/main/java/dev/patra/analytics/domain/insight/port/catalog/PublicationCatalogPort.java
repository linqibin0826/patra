package dev.patra.analytics.domain.insight.port.catalog;

import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/// 向 catalog 核对文献、取最新收录与同主题邻居。失败一律抛
/// [dev.patra.analytics.domain.insight.exception.InsightUnavailableException]。
public interface PublicationCatalogPort {

  /// 哪些文献可以展示：存在、没有软删、不是撤稿文献。
  ///
  /// @param ids 文献 ID，个数不限
  /// @return 合格的 ID
  Set<Long> eligibleAmong(Collection<Long> ids);

  /// 最新收录的可用文献。
  ///
  /// @param limit 条数，1 ~ 100
  /// @return 文献 ID，收录时间新的在前
  List<Long> latestIds(int limit);

  /// 同主题邻居：先同主要主题词、再同期刊。
  ///
  /// @param articleId 文献 ID
  /// @param limit 条数，1 ~ 20
  /// @return 来源为 `SAME_MESH` 或 `SAME_VENUE`，按展示顺序
  List<AlsoReadItem> subjectNeighbors(long articleId, int limit);
}
