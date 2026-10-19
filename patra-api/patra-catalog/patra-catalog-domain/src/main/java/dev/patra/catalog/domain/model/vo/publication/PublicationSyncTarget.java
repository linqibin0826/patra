package dev.patra.catalog.domain.model.vo.publication;

/// 增量同步要覆盖的库内文献（按 PMID 查到，包含已软删除的行）。
///
/// @param publicationId 内部 ID，覆盖后保持不变
/// @param pmid PMID
/// @param doi 库里当前的 DOI，新 DOI 冲突时回退到它
/// @param deleted 是否已软删除（覆盖时复活）
/// @author linqibin
/// @since 0.11.0
public record PublicationSyncTarget(long publicationId, String pmid, String doi, boolean deleted) {

  /// 创建同步目标。
  ///
  /// @param publicationId 内部 ID
  /// @param pmid PMID
  /// @param doi 当前 DOI
  /// @param deleted 是否已软删除
  /// @return 同步目标
  public static PublicationSyncTarget of(
      long publicationId, String pmid, String doi, boolean deleted) {
    return new PublicationSyncTarget(publicationId, pmid, doi, deleted);
  }
}
