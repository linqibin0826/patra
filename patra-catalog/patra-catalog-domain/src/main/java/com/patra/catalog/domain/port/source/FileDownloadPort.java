package com.patra.catalog.domain.port.source;

import com.patra.catalog.domain.exception.FileDownloadException;
import java.net.URI;
import java.nio.file.Path;

/// 文件下载端口（外部 HTTP 资源下载）。
///
/// **设计原则**：
///
/// - Domain 层定义接口，隐藏技术实现细节
/// - Infrastructure 层使用 RestClient 实现
/// - 返回本地文件路径，调用方无需关心下载过程
///
/// **使用场景**：
///
/// - MeSH 数据文件下载（约 300MB XML 文件）
/// - 其他需要从远程 URL 下载文件的场景
///
/// **实现要求**：
///
/// - 下载到系统临时目录
/// - 文件命名应包含唯一标识符，避免并发冲突
/// - 调用方负责在使用完毕后清理临时文件
///
/// @author linqibin
/// @since 0.1.0
public interface FileDownloadPort {

  /// 下载文件到系统临时目录。
  ///
  /// 将指定 URL 的文件下载到系统临时目录，返回本地文件路径。
  /// 文件命名规则由实现类决定，应保证唯一性。
  ///
  /// **注意**：调用方负责在使用完毕后清理临时文件。
  ///
  /// @param url 远程文件 URL（必须是 HTTP 或 HTTPS 协议）
  /// @return 本地临时文件路径
  /// @throws FileDownloadException 下载失败时（网络错误、HTTP 错误、IO 错误）
  Path downloadToTemp(URI url);
}
