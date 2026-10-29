package dev.patra.catalog.infra.portal.persistence;

/// [PublicationDetailReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @author linqibin
/// @since 0.1.0
record PublicationMeshRow(String descriptorUi, String term, Boolean major) {}
