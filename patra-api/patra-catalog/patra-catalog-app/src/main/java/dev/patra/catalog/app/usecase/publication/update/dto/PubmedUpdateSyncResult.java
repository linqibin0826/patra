package dev.patra.catalog.app.usecase.publication.update.dto;

import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileCounts;

/// 一次增量同步运行的结果。
///
/// @param filesProcessed 本次处理成功的文件数
/// @param lastSeq 最后一个处理成功的文件号，没有处理文件时为空
/// @param total 本次所有文件的计数之和
/// @param skippedByLock 是否因另一个实例在运行而跳过
/// @author linqibin
/// @since 0.11.0
public record PubmedUpdateSyncResult(
    int filesProcessed, Integer lastSeq, PubmedUpdateFileCounts total, boolean skippedByLock) {

  /// 创建结果。
  ///
  /// @param filesProcessed 处理文件数
  /// @param lastSeq 最后文件号
  /// @param total 计数之和
  /// @param skippedByLock 是否跳过
  /// @return 结果
  public static PubmedUpdateSyncResult of(
      int filesProcessed, Integer lastSeq, PubmedUpdateFileCounts total, boolean skippedByLock) {
    return new PubmedUpdateSyncResult(filesProcessed, lastSeq, total, skippedByLock);
  }

  /// 因另一个实例在运行而跳过的结果。
  ///
  /// @return 结果
  public static PubmedUpdateSyncResult ofSkippedByLock() {
    return new PubmedUpdateSyncResult(0, null, PubmedUpdateFileCounts.empty(), true);
  }

  /// 给任务日志看的一句话摘要。
  ///
  /// @return 摘要
  public String message() {
    if (skippedByLock) {
      return "另一个增量同步实例正在运行，本次跳过";
    }
    return "增量同步完成：处理 %d 个文件，最后文件号 %s，更新 %d、新建 %d、删除 %d、库里没有 %d、无效 %d、DOI 冲突 %d、出错跳过 %d"
        .formatted(
            filesProcessed,
            lastSeq,
            total.updated(),
            total.created(),
            total.deleted(),
            total.skippedAbsent(),
            total.skippedInvalid(),
            total.doiConflicts(),
            total.skipErrors());
  }
}
