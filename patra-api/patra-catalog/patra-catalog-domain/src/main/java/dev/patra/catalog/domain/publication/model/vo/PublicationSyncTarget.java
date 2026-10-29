package dev.patra.catalog.domain.publication.model.vo;

import lombok.Builder;

/// 一个 PMID 在库里的同步目标：文献行（可能已软删）与来源位置，两者可能只有其一。
///
/// - 只有文献、没有来源行：PAP-110 之前导入的旧数据，`sourceRank` 为 null，视为最旧
/// - 只有来源行、没有文献：墓碑（`ALL` 范围下被删除、库里从没有过的 PMID），`publicationId` 为 null
///
/// @param publicationId 文献主键；墓碑时为 null
/// @param pmid PMID
/// @param doi 当前 DOI，可空
/// @param deleted 文献是否已软删
/// @param sourceRank 来源位置（见 [SourcePosition]）；没有来源行时为 null
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationSyncTarget(
    Long publicationId, String pmid, String doi, boolean deleted, Integer sourceRank) {

  /// 是否墓碑（只有来源行、没有文献）。
  ///
  /// @return `publicationId` 为 null 时为 `true`
  public boolean isTombstone() {
    return publicationId == null;
  }
}
