package com.patra.catalog.domain.model.read.venue;

import cn.hutool.core.lang.Assert;
import java.time.Instant;

/// 期刊列表项读模型。
///
/// @param id 期刊主键 ID
/// @param title 期刊标题
/// @param titleZh 中文标题（可空，来自 Wikidata）
/// @param issnL ISSN-L（可空）
/// @param nlmId NLM ID（可空）
/// @param provenanceCode 数据来源编码
/// @param countryCode 国家编码（可空）
/// @param lastSyncedAt 最后同步时间（可空）
public record VenueSummaryReadModel(
    Long id,
    String title,
    String titleZh,
    String issnL,
    String nlmId,
    String provenanceCode,
    String countryCode,
    Instant lastSyncedAt) {

  /// 构造期刊列表项读模型并执行参数校验。
  public VenueSummaryReadModel {
    Assert.notNull(id, "期刊 ID 不能为空");
    Assert.notBlank(title, "期刊标题不能为空");
    Assert.notBlank(provenanceCode, "数据来源不能为空");
  }
}
