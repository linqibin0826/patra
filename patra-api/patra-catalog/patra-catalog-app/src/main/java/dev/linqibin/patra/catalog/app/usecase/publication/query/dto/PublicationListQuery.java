package dev.linqibin.patra.catalog.app.usecase.publication.query.dto;

import lombok.Builder;

/// 文献列表查询参数。
///
/// 所有字段均可为 null，由 [PublicationQueryService] 归一化处理。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationListQuery(
    Integer page,
    Integer pageSize,
    String q,
    Integer yearFrom,
    Integer yearTo,
    String languageBase,
    Boolean isOa,
    String oaStatus,
    Long venueId,
    Long venueInstanceId,
    String pmid,
    String doi,
    String provenanceCode,
    String publicationStatus,
    String sortBy) {}
