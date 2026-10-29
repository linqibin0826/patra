package dev.patra.catalog.infra.adapter.read;

import java.time.Instant;

/// [PublicationSearchReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @param publicationMonth 出版月。
/// @param publicationDay 出版日。
/// @param publicationTypesAgg 全部类型值，单元分隔符（U+001F）拼接，按 type_order 升序；无类型时为 null。
/// @param authorNames 作者展示名，单元分隔符（U+001F）拼接，按 author_order 升序；无作者时为 null。
/// @param abstractRaw 摘要原文全文（含内联标记，不在 SQL 里预截断——截开长 annotation 会让隐藏公式注释露出来）；无摘要时为 null。
/// @author linqibin
/// @since 0.1.0
record PublicationSearchRow(
    Long id,
    String title,
    Long venueId,
    String venueName,
    Integer publicationYear,
    Integer publicationMonth,
    Integer publicationDay,
    Integer citationCount,
    String doi,
    String pmid,
    String provenanceCode,
    String publicationTypesAgg,
    String authorNames,
    String abstractRaw,
    Instant lastSyncedAt) {}
