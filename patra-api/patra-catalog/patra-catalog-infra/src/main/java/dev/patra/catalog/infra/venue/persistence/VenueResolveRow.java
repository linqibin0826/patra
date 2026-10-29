package dev.patra.catalog.infra.venue.persistence;

/// [dev.patra.catalog.infra.venue.persistence.dao.VenueResolveDao] 的接口投影。
///
/// getter 名与 SQL 列别名一一对应（别名在 SQL 中加双引号保持 camelCase）。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueResolveRow {

  /// 期刊主键。
  ///
  /// @return 主键
  Long getId();

  /// 主 NLM。
  ///
  /// @return 主 NLM，可空
  String getNlmId();

  /// 期刊全称。
  ///
  /// @return 全称
  String getTitle();

  /// MEDLINE 刊名缩写。
  ///
  /// @return 缩写，可空
  String getAbbreviatedTitle();

  /// 是否已软删。
  ///
  /// @return `deleted_at IS NOT NULL`
  Boolean getDeleted();
}
