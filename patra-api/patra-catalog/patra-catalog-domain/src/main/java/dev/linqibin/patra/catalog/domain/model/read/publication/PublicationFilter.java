package dev.linqibin.patra.catalog.domain.model.read.publication;

import lombok.Builder;

/// 文献出版物列表筛选条件。
///
/// 所有字段均可为 null，表示不筛选该维度。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationFilter(
    String keyword,
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
