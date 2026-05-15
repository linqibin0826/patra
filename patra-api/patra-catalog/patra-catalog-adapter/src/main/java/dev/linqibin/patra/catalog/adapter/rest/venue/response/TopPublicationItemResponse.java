package dev.linqibin.patra.catalog.adapter.rest.venue.response;

/// 刊级 Top N 高被引响应项。
///
/// 字段与 {@link dev.linqibin.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel}
/// 的子集对齐，由 {@code PublicationApiConverter#toTopItemResponse} 映射。
///
/// @param id              文献主键 ID
/// @param title           文献标题
/// @param publicationYear 出版年份
/// @param citationCount   被引次数
/// @param doi             Digital Object Identifier
public record TopPublicationItemResponse(
    Long id, String title, Integer publicationYear, Integer citationCount, String doi) {}
