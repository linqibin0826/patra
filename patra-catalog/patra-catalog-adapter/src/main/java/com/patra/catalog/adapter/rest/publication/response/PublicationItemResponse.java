package com.patra.catalog.adapter.rest.publication.response;

import java.time.Instant;

/// Publication 列表项响应。
///
/// @param id 文献 ID
/// @param title 标题
/// @param pmid PubMed ID（可空）
/// @param doi DOI（可空）
/// @param publicationYear 出版年份（可空）
/// @param languageCode 语言代码（可空）
/// @param isOa 是否有 OA 版本（可空）
/// @param oaStatus OA 状态（可空）
/// @param venueId 载体 ID（可空）
/// @param venueName 载体名称（可空）
/// @param citationCount 被引次数（可空）
/// @param lastSyncedAt 最后同步时间（可空）
public record PublicationItemResponse(
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
    Instant lastSyncedAt) {}
