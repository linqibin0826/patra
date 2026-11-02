package dev.patra.analytics.app.insight.usecase.query;

import dev.patra.analytics.domain.insight.model.vo.HotReadingSummary;
import dev.patra.analytics.domain.insight.policy.HotReadingRule;
import dev.patra.analytics.domain.insight.port.read.HotReadingReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 首页热读（insight spec §9.2）：只读 PG 快照，不碰 ClickHouse 与 catalog。不开事务。
@Service
@RequiredArgsConstructor
public class HotReadingQueryService {

  private final HotReadingReadPort hotReadings;

  /// 一页（14 篇）：上榜的在前、补位的在后。
  ///
  /// @return 热读页；任务从没跑过时为空页
  public HotReadingSummary page() {
    return hotReadings.summary(HotReadingRule.PAGE_SIZE);
  }
}
