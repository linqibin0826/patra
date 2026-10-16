package dev.patra.catalog.infra.adapter.read;

/// 文献资助信息子表投影接口，对应 `PublicationDetailDao.findFundingByPublicationId` 查询结果。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationFundingRow {

  String getFunder();

  String getGrantId();

  String getCountry();
}
