package dev.patra.catalog.domain.publication.model.read;

import lombok.Builder;

/// 文献解析读模型：库里存在的一篇文献（含软删），供 interaction 的对象登记表首次解析与对账。
///
/// @param id 文献主键
/// @param pmid PubMed ID（库里可为空，照实返回）
/// @param title 标题（含内联标记原文）
/// @param venueName 期刊全称（`cat_venue.title`，没有载体时为 null）
/// @param publicationYear 出版年份（可空）
/// @param publishedDate 出版日期，按实际精度输出 `yyyy` / `yyyy-MM` / `yyyy-MM-dd`（可空）
/// @param deleted 是否已软删
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationResolveReadModel(
    Long id,
    String pmid,
    String title,
    String venueName,
    Integer publicationYear,
    String publishedDate,
    boolean deleted) {}
