package dev.patra.catalog.domain.publication.port.repository;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileRecord;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileRef;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/// PubMed 增量同步进度的持久化。
///
/// @author linqibin
/// @since 0.11.0
public interface PubmedFileRepository {

  /// 返回最后一个成功文件的文件号。
  ///
  /// @param kind 文件种类，只看这一种的进度行
  /// @return 文件号，没有成功文件时为空
  OptionalInt findLastCompletedSeq(PubmedFileKind kind);

  /// 返回最小的未完成（失败或中断）文件的文件号。
  ///
  /// 手动 `fromFile` 处理了更后面的文件时，最大成功文件号会越过失败的文件，续跑要从这里开始。
  ///
  /// @param kind 文件种类，只看这一种的进度行
  /// @return 文件号，没有未完成文件时为空
  OptionalInt findFirstUnfinishedSeq(PubmedFileKind kind);

  /// 按文件名查记录。
  ///
  /// @param fileName 文件名
  /// @return 记录
  Optional<PubmedFileRecord> findByFileName(String fileName);

  /// 标记文件开始处理；已有记录时覆盖为 RUNNING 并清空计数、结束时间与错误。
  ///
  /// @param ref 文件
  /// @param scope 导入范围
  /// @param generation 这次所用的作业 generation，可空
  /// @param startedAt 开始时间
  void markRunning(
      PubmedFileRef ref, PubmedImportScope scope, String generation, Instant startedAt);

  /// 标记文件处理成功并写入计数。
  ///
  /// @param fileName 文件名
  /// @param counts 计数
  /// @param finishedAt 结束时间
  void markCompleted(String fileName, PubmedFileCounts counts, Instant finishedAt);

  /// 标记文件处理失败，错误信息截断到 2000 字符。
  ///
  /// @param fileName 文件名
  /// @param error 错误信息
  /// @param finishedAt 结束时间
  void markFailed(String fileName, String error, Instant finishedAt);
}
