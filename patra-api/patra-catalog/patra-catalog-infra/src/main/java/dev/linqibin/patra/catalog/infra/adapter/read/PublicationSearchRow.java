package dev.linqibin.patra.catalog.infra.adapter.read;

import java.time.Instant;

/// [dev.linqibin.patra.catalog.infra.persistence.dao.PublicationSearchDao#findSearchPage] 的接口投影。
///
/// getter 名与 SQL 列别名一一对应（别名在 SQL 中加双引号保持 camelCase）。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationSearchRow {

  Long getId();

  String getTitle();

  Long getVenueId();

  String getVenueName();

  Integer getPublicationYear();

  Integer getCitationCount();

  String getDoi();

  String getPmid();

  String getProvenanceCode();

  /// 全部类型值，单元分隔符（U+001F）拼接，按 type_order 升序；无类型时为 null。
  String getPublicationTypesAgg();

  /// 作者展示名，单元分隔符（U+001F）拼接，按 author_order 升序；无作者时为 null。
  String getAuthorNames();

  /// 摘要原文全文（含内联标记，不在 SQL 里预截断——截开长 annotation 会让隐藏公式注释露出来）；无摘要时为 null。
  String getAbstractRaw();

  Instant getLastSyncedAt();
}
