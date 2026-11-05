package dev.patra.analytics.app.insight.usecase.query;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import dev.patra.analytics.domain.insight.policy.AlsoReadRule;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import dev.patra.analytics.domain.insight.port.read.CoReadingReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 看过这篇的人还看了（insight spec §6.3）：共现快照的前 6 篇，不足时向 catalog 要同主题邻居补位。不开事务。
///
/// catalog 不可用时只返回共现部分，不报错：文献页这块按实际篇数展示。
@Slf4j
@Service
@RequiredArgsConstructor
public class AlsoReadQueryService {

  private final CoReadingReadPort coReadings;
  private final PublicationCatalogPort catalog;

  /// 最多 6 篇，按展示顺序。
  ///
  /// @param articleId 文献 ID
  /// @return 一起读的在前，补位的在后
  public List<AlsoReadItem> alsoRead(long articleId) {
    List<Long> coRead = coReadings.relatedOf(articleId, AlsoReadRule.DISPLAY);
    if (coRead.size() >= AlsoReadRule.DISPLAY) {
      return AlsoReadRule.assemble(coRead, List.of());
    }
    List<AlsoReadItem> neighbors;
    try {
      neighbors = catalog.subjectNeighbors(articleId, AlsoReadRule.NEIGHBOR_FETCH);
    } catch (InsightUnavailableException e) {
      log.warn("取同主题邻居失败，只返回一起读的 {} 篇：{}", coRead.size(), e.getMessage());
      neighbors = List.of();
    }
    return AlsoReadRule.assemble(coRead, neighbors);
  }
}
