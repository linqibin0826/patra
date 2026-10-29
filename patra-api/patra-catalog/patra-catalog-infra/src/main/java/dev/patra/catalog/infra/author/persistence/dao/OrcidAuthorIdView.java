package dev.patra.catalog.infra.author.persistence.dao;

/// ORCID → 作者 ID 轻量投影（chunk 级批量软关联专用，避免加载完整 Author 聚合）。
///
/// @author linqibin
/// @since 0.6.0
public interface OrcidAuthorIdView {

  /// ORCID 标识符。
  String getOrcid();

  /// 已消歧作者 ID。
  Long getAuthorId();
}
