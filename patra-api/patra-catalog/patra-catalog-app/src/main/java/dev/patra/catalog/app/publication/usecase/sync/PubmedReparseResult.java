package dev.patra.catalog.app.publication.usecase.sync;

import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;

/// 本地重算的结果。
///
/// @param counts 计数
/// @param skippedByLock 是否因另一实例在运行而跳过
/// @author linqibin
/// @since 0.1.0
public record PubmedReparseResult(PubmedFileCounts counts, boolean skippedByLock) {

  /// 创建结果。
  ///
  /// @param counts 计数
  /// @param skippedByLock 是否因锁跳过
  /// @return 结果
  public static PubmedReparseResult of(PubmedFileCounts counts, boolean skippedByLock) {
    return new PubmedReparseResult(counts, skippedByLock);
  }

  /// 给 XXL-Job 日志的一行说明。
  ///
  /// @return 说明
  public String message() {
    if (skippedByLock) {
      return "另一个 PubMed 导入或重算实例正在运行，本次跳过";
    }
    return "PubMed 本地重算完成：" + PubmedFileImportResult.describe(counts);
  }
}
