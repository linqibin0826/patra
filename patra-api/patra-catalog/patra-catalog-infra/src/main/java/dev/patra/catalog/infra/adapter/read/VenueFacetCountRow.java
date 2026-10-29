package dev.patra.catalog.infra.adapter.read;

/// [VenueBrowseReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @author linqibin
/// @since 0.1.0
record VenueFacetCountRow(String value, long count) {}
