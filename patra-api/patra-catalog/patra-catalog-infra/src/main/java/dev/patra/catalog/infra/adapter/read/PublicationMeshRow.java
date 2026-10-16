package dev.patra.catalog.infra.adapter.read;

/// 文献 MeSH 标引子表投影接口，对应 `PublicationDetailDao.findMeshHeadingsByPublicationId` 查询结果。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationMeshRow {

  String getDescriptorUi();

  String getTerm();

  Boolean getMajor();
}
