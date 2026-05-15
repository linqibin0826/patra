package dev.linqibin.patra.catalog.domain.port.source;

import dev.linqibin.patra.catalog.domain.exception.FileDownloadException;
import java.net.URI;

/// 文件下载端口（统一下载方式）。
///
/// 下载远程文件到本地临时目录，解耦 HTTP 连接生命周期与数据处理速度。
///
/// **设计原则**：
///
/// - Domain 层定义接口，隐藏下载客户端实现细节
/// - Infrastructure 层使用 DownloadClient.downloadToTemp() 实现
/// - 调用方负责删除临时文件（Handler 的 finally 块或 ItemReader.close()）
///
/// **使用场景**：
///
/// - App 层 Handler 导入（下载后从本地文件读取解析）
/// - Spring Batch ItemReader 需要本地文件解析（避免 PrematureCloseException）
/// - 慢速 Writer 场景（DB 插入阻塞导致 HTTP 超时）
///
/// **使用示例**：
///
/// ```java
/// FileDownloadResult result = fileDownloadPort.download(URI.create(url));
/// try (InputStream in = Files.newInputStream(result.filePath())) {
///     parser.parse(in)...
/// } finally {
///     Files.deleteIfExists(result.filePath());
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see FileDownloadResult
public interface FileDownloadPort {

  /// 下载远程文件到临时目录。
  ///
  /// 调用方**必须**在使用完毕后删除临时文件。
  ///
  /// @param url 远程资源 URL（支持 HTTP、HTTPS 或 FTP 协议）
  /// @return 文件下载结果（包含临时文件路径和文件大小）
  /// @throws FileDownloadException 下载失败时（网络错误、HTTP 错误、磁盘 IO 错误）
  FileDownloadResult download(URI url);
}
