package dev.patra.catalog.infra.portal.persistence;

/// [PublicationDetailReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @param type 日期类型。
/// @param year 年。
/// @param month 月。
/// @param day 日。
/// @param precision 精度。
/// @author linqibin
/// @since 0.1.0
record PublicationDateRow(
    String type, Integer year, Integer month, Integer day, String precision) {}
