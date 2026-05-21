package dev.linqibin.starter.restclient.download;

/// 文件写入策略。
///
/// 控制目标文件已存在时的行为。
///
/// @author linqibin
/// @since 0.1.0
public enum WriteStrategy {

  /// 覆盖已有文件。
  OVERWRITE,

  /// 如果文件已存在则跳过下载。
  SKIP,

  /// 如果文件已存在则失败。
  FAIL
}
