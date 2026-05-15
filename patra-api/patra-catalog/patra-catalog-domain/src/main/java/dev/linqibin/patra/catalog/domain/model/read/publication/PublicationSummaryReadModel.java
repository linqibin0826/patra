package dev.linqibin.patra.catalog.domain.model.read.publication;

import cn.hutool.core.lang.Assert;
import java.time.Instant;

/// 文献出版物列表项读模型。
///
/// 用于 CQRS 读端列表查询返回的摘要信息，包含文献的核心标识和概要数据。
/// `venueId` 直接来自 Publication 主表，`venueName` 来自关联的 Venue 表，
/// 由 Infra 层 ReadAdapter 组装。
///
/// @author linqibin
/// @since 0.1.0
public record PublicationSummaryReadModel(
    Long id,
    String title,
    String pmid,
    String doi,
    Integer publicationYear,
    String languageCode,
    Boolean isOa,
    String oaStatus,
    Long venueId,
    String venueName,
    Integer citationCount,
    Instant lastSyncedAt) {

  public PublicationSummaryReadModel {
    Assert.notNull(id, "出版物 ID 不能为空");
    Assert.notBlank(title, "出版物标题不能为空");
  }
}
