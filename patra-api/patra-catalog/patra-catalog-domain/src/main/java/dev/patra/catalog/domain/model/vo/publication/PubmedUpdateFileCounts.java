package dev.patra.catalog.domain.model.vo.publication;

import lombok.Builder;

/// 一个 updatefiles 文件的处理计数。
///
/// @param created 新建的文献数（仅 `ALL` 范围）
/// @param updated 覆盖更新的文献数
/// @param deleted 按 `DeleteCitation` 软删除的文献数
/// @param skippedAbsent `EXISTING_ONLY` 下库里没有而跳过的记录数
/// @param skippedInvalid 处理器判为无效（缺 PMID、匹配不到期刊等）而跳过的记录数
/// @param doiConflicts 新 DOI 已属于别的文献、保留旧 DOI 的次数
/// @param skipErrors 写入出错被 Spring Batch 跳过的记录数
/// @author linqibin
/// @since 0.11.0
@Builder
public record PubmedUpdateFileCounts(
    int created,
    int updated,
    int deleted,
    int skippedAbsent,
    int skippedInvalid,
    int doiConflicts,
    int skipErrors) {

  /// 全为 0 的计数。
  ///
  /// @return 空计数
  public static PubmedUpdateFileCounts empty() {
    return new PubmedUpdateFileCounts(0, 0, 0, 0, 0, 0, 0);
  }

  /// 逐项相加。
  ///
  /// @param other 另一份计数
  /// @return 和
  public PubmedUpdateFileCounts plus(PubmedUpdateFileCounts other) {
    return new PubmedUpdateFileCounts(
        created + other.created,
        updated + other.updated,
        deleted + other.deleted,
        skippedAbsent + other.skippedAbsent,
        skippedInvalid + other.skippedInvalid,
        doiConflicts + other.doiConflicts,
        skipErrors + other.skipErrors);
  }
}
