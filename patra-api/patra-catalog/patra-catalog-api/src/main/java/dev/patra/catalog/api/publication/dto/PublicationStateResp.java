package dev.patra.catalog.api.publication.dto;

/// 文献在 catalog 里的状态。
///
/// @author linqibin
/// @since 0.1.0
public enum PublicationStateResp {

  /// 正常。
  ACTIVE,

  /// 已软删（PubMed `DeleteCitation`），可能在之后的更新文件里复活。
  DELETED
}
