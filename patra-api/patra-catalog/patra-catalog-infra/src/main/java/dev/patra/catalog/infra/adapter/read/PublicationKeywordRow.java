package dev.patra.catalog.infra.adapter.read;

/// 文献关键词子表投影接口，对应 `PublicationDetailDao.findKeywordsByPublicationId` 查询结果。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationKeywordRow {

  String getTerm();
}
