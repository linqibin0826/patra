package dev.patra.catalog.infra.portal.persistence;

/// [CitationRecordReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @param publicationId 文献主键
/// @param family 姓，可空
/// @param given 名，可空
/// @param initials 名的缩写，可空
/// @param collective 团体作者名，可空
/// @author linqibin
/// @since 0.1.0
record CitationAuthorRow(
    Long publicationId, String family, String given, String initials, String collective) {}
