package dev.patra.catalog.domain.publication.model.vo;

import lombok.Builder;

/// 一个 updatefiles 文件的处理计数。
///
/// @param created 新建的文献数（仅 `ALL` 范围）
/// @param updated 覆盖更新的文献数
/// @param deleted 按 `DeleteCitation` 软删除的文献数
/// @param skippedAbsent `EXISTING_ONLY` 下库里没有而跳过的记录数
/// @param skippedInvalid 处理器判为无效（缺 PMID、匹配不到期刊等）而跳过的记录数
/// @param doiConflicts 新 DOI 已属于别的文献、保留旧 DOI 的次数
/// @param skippedStale 比库里版本旧而跳过的记录数（来源位置守卫）
/// @param skipErrors 写入出错被 Spring Batch 跳过的记录数
/// @author linqibin
/// @since 0.11.0
@Builder
public record PubmedFileCounts(
    int created,
    int updated,
    int deleted,
    int skippedAbsent,
    int skippedInvalid,
    int doiConflicts,
    int skipErrors,
    int skippedStale) {

  /// 全为 0 的计数。
  ///
  /// @return 空计数
  public static PubmedFileCounts empty() {
    return new PubmedFileCounts(0, 0, 0, 0, 0, 0, 0, 0);
  }

  /// 逐项相加。
  ///
  /// @param other 另一份计数
  /// @return 和
  public PubmedFileCounts plus(PubmedFileCounts other) {
    return new PubmedFileCounts(
        created + other.created,
        updated + other.updated,
        deleted + other.deleted,
        skippedAbsent + other.skippedAbsent,
        skippedInvalid + other.skippedInvalid,
        doiConflicts + other.doiConflicts,
        skipErrors + other.skipErrors,
        skippedStale + other.skippedStale);
  }
}
