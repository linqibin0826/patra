package dev.patra.catalog.infra.adapter.read;

/// [dev.patra.catalog.infra.persistence.dao.PublicationResolveDao] 的接口投影。
///
/// getter 名与 SQL 列别名一一对应（别名在 SQL 中加双引号保持 camelCase）。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationResolveRow {

  /// 文献主键。
  ///
  /// @return 主键
  Long getId();

  /// PubMed ID。
  ///
  /// @return PMID，可空
  String getPmid();

  /// 标题原文。
  ///
  /// @return 标题
  String getTitle();

  /// 期刊全称。
  ///
  /// @return 期刊名，没有载体时为 null
  String getVenueName();

  /// 出版年份。
  ///
  /// @return 年份，可空
  Integer getPublicationYear();

  /// 是否已软删。
  ///
  /// @return `deleted_at IS NOT NULL`
  Boolean getDeleted();
}
