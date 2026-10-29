package dev.patra.catalog.domain.publication.model.enums;

/// PubMed updatefiles 单个文件的同步状态。
///
/// @author linqibin
/// @since 0.11.0
public enum PubmedFileStatus {

  /// 正在处理
  RUNNING,

  /// 处理成功
  COMPLETED,

  /// 处理失败，下次运行从这个文件重试
  FAILED
}
