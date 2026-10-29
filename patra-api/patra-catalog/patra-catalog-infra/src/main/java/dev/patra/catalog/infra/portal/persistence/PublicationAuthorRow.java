package dev.patra.catalog.infra.portal.persistence;

/// [PublicationDetailReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @param affiliation 第一机构字符串（按 affiliation_order ASC LIMIT 1 取得，可为 null）。
/// @author linqibin
/// @since 0.1.0
record PublicationAuthorRow(
    Integer order, Boolean first, Boolean corresponding, String name, String affiliation) {}
