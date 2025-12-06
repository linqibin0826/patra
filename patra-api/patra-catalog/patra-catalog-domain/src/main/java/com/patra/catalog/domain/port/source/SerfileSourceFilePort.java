package com.patra.catalog.domain.port.source;

import java.net.URI;
import java.nio.file.Path;

/// Serfile 数据源文件端口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 返回本地临时文件路径，由调用方负责清理
///
/// **主要使用场景**：
///
/// 从 NLM FTP 或 HTTP 服务器下载 SerfileBase XML 文件
///
/// @author linqibin
/// @since 0.1.0
public interface SerfileSourceFilePort {

  /// 获取 Serfile XML 文件。
  ///
  /// 从远程 URL 下载文件到本地临时目录。
  /// 调用方负责在使用完毕后删除临时文件。
  ///
  /// **使用示例**：
  ///
  /// ```java
  /// Path tempFile = serfileSourceFilePort.fetch(URI.create("https://..."));
  /// try {
  ///     // 使用文件
  /// } finally {
  ///     Files.deleteIfExists(tempFile);
  /// }
  /// ```
  ///
  /// @param remoteUrl 远程 URL
  /// @return 本地临时文件路径
  /// @throws com.patra.catalog.domain.exception.SerfileDownloadException 下载失败时抛出
  Path fetch(URI remoteUrl);
}
