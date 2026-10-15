package dev.patra.catalog.infra.adapter.read;

import java.time.LocalDate;

/// 文献日期子表投影接口，对应 `PublicationDetailDao.findDatesByPublicationId` 查询结果。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationDateRow {

  String getType();

  LocalDate getDate();
}
