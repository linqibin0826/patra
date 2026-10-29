package dev.patra.catalog.app.publication.usecase.sync;

import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;

/// 处理一段 PubMed 文件的结果。
///
/// @param filesProcessed 处理的文件数
/// @param lastSeq 最后处理的文件序号，没有处理任何文件时为 null
/// @param total 计数合计
/// @param skippedByLock 是否因另一实例在运行而跳过
/// @author linqibin
/// @since 0.1.0
public record PubmedFileImportResult(
    int filesProcessed, Integer lastSeq, PubmedFileCounts total, boolean skippedByLock) {

  /// 创建结果。
  ///
  /// @param filesProcessed 处理的文件数
  /// @param lastSeq 最后文件序号，可空
  /// @param total 计数合计
  /// @param skippedByLock 是否因锁跳过
  /// @return 结果
  public static PubmedFileImportResult of(
      int filesProcessed, Integer lastSeq, PubmedFileCounts total, boolean skippedByLock) {
    return new PubmedFileImportResult(filesProcessed, lastSeq, total, skippedByLock);
  }

  /// 因锁跳过。
  ///
  /// @return 结果
  public static PubmedFileImportResult ofSkippedByLock() {
    return new PubmedFileImportResult(0, null, PubmedFileCounts.empty(), true);
  }

  /// 给 XXL-Job 日志的一行说明。
  ///
  /// @return 说明
  public String message() {
    if (skippedByLock) {
      return "另一个 PubMed 导入或重算实例正在运行，本次跳过";
    }
    return "PubMed 导入完成：处理 %d 个文件，最后文件号 %s，%s".formatted(filesProcessed, lastSeq, describe(total));
  }

  /// 计数的统一描述（导入与重算共用）。
  ///
  /// @param c 计数
  /// @return 描述
  public static String describe(PubmedFileCounts c) {
    return "更新 %d、新建 %d、删除 %d、库里没有 %d、比库里旧 %d、无效 %d、DOI 冲突 %d、出错跳过 %d"
        .formatted(
            c.updated(),
            c.created(),
            c.deleted(),
            c.skippedAbsent(),
            c.skippedStale(),
            c.skippedInvalid(),
            c.doiConflicts(),
            c.skipErrors());
  }
}
