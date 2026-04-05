package com.patra.catalog.domain.model.read.venue;

import java.util.Objects;
import lombok.Builder;

/// 期刊列表摘要读模型。
///
/// 用于期刊列表页展示，包含学术评价指标摘要。
/// 数据来源：VenueEntity 的核心字段 + citationMetrics/letPubData/openAccess JSON 列。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueSummaryReadModel(
    Long id,
    String title,
    String titleZh,
    String countryCode,
    String imageUrl,
    Integer hIndex,
    String jifQuartile,
    String casMajorQuartile,
    Boolean casTopJournal,
    String warningListStatus,
    Boolean isOa,
    String researchDirection) {

  /// 构造期刊列表摘要读模型并执行参数校验。
  public VenueSummaryReadModel {
    Objects.requireNonNull(id, "id must not be null");
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
  }
}
