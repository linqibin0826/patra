package com.patra.catalog.domain.port.source;

import com.patra.catalog.domain.exception.FileDownloadException;
import java.net.URI;

/// 流式下载端口（远程资源流式获取）。
///
/// **设计原则**：
///
/// - Domain 层定义接口，隐藏网络客户端实现细节
/// - Infrastructure 层使用 WebClient（HTTP/HTTPS）或 FTPClient（FTP）实现
/// - 返回响应体流，无磁盘落盘
///
/// **使用场景**：
///
/// - 数据只需顺序读取一次
/// - 不需要断点续传能力
/// - 希望减少磁盘 IO 开销
///
/// **使用示例**：
///
/// ```java
/// try (StreamingDownloadResult result = streamingDownloadPort.download(uri)) {
///     List<Entity> entities = parserPort.parse(result.inputStream()).toList();
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see StreamingDownloadResult
public interface StreamingDownloadPort {

  /// 流式下载远程资源。
  ///
  /// 建立网络连接并返回响应体输入流，无磁盘落盘。
  /// 调用方**必须**使用 try-with-resources 确保流正确关闭。
  ///
  /// **注意事项**：
  ///
  /// - 返回的流与网络连接绑定，关闭流会释放连接
  /// - 大文件场景使用 `streamingWebClient`（30 分钟超时）
  /// - 网络中断无法恢复，需重新调用
  ///
  /// @param url 远程资源 URL（支持 HTTP、HTTPS 或 FTP 协议）
  /// @return 流式下载结果（包含输入流和元数据）
  /// @throws FileDownloadException 下载失败时（网络错误、HTTP 错误）
  StreamingDownloadResult download(URI url);
}
