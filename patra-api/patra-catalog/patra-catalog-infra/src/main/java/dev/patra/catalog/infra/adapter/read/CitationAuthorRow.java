package dev.patra.catalog.infra.adapter.read;

/// [dev.patra.catalog.infra.persistence.dao.CitationRecordDao#findAuthors] 的接口投影。
///
/// @author linqibin
/// @since 0.1.0
public interface CitationAuthorRow {

  /// 文献主键
  ///
  /// @return 文献主键
  Long getPublicationId();

  /// 姓，可空
  ///
  /// @return 姓，可空
  String getFamily();

  /// 名，可空
  ///
  /// @return 名，可空
  String getGiven();

  /// 名的缩写，可空
  ///
  /// @return 名的缩写，可空
  String getInitials();

  /// 团体作者名，可空
  ///
  /// @return 团体作者名，可空
  String getCollective();
}
