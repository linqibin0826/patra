package dev.linqibin.patra.catalog.domain.model.read.venue;

import cn.hutool.core.lang.Assert;

/// 期刊卷/期摘要读模型。
///
/// 用于 Venue Instances 列表查询，包含卷期信息和关联的文献数量。
///
/// @param id 实例主键 ID
/// @param volume 卷号
/// @param issue 期号
/// @param publicationYear 出版年份
/// @param publicationMonth 出版月份
/// @param publicationDay 出版日期
/// @param publicationCount 关联文献数量
public record VenueInstanceSummaryReadModel(
    Long id,
    String volume,
    String issue,
    Integer publicationYear,
    Integer publicationMonth,
    Integer publicationDay,
    long publicationCount) {

  /// 构造并验证实例摘要读模型。
  public VenueInstanceSummaryReadModel {
    Assert.notNull(id, "Instance ID 不能为空");
  }
}
