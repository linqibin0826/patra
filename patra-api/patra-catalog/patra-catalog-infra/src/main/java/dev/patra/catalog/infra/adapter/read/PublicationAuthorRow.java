package dev.patra.catalog.infra.adapter.read;

/// 文献作者子表投影接口，对应 `PublicationDetailDao.findAuthorsByPublicationId` 查询结果。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationAuthorRow {

  Integer getOrder();

  Boolean getFirst();

  Boolean getCorresponding();

  String getName();

  /// 第一机构字符串（按 affiliation_order ASC LIMIT 1 取得，可为 null）。
  String getAffiliation();
}
