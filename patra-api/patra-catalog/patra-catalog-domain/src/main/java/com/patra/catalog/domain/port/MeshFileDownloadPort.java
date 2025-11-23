package com.patra.catalog.domain.port;

import java.io.File;

/// MeSH 文件下载接口（Port）。
///
/// 定义 MeSH XML 文件的下载和校验操作，由 Infrastructure 层实现。
///
/// **设计原则**：
///
/// - 纯接口定义：不包含实现逻辑
///   - 面向领域概念：方法名称和参数符合领域语言
///   - 六边形架构：领域层定义接口，基础设施层实现
///   - 异常处理：实现层负责处理网络异常和 IO 异常
///
/// **实现说明**：
///
/// - Infrastructure 层使用 Apache HttpClient 或 OkHttp 实现下载
///   - 支持断点续传和重试机制
///   - 下载到临时目录，校验通过后移动到目标目录
///   - 使用 MD5 哈希验证文件完整性
///
/// **使用示例**：
///
/// ```java
/// // 1. 下载 XML 文件
/// File xmlFile =
// meshFileDownloadPort.download("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc{year}.xml");
///
/// // 2. 验证文件完整性
/// boolean valid = meshFileDownloadPort.validateChecksum(xmlFile, "a1b2c3d4e5f6...");
///
/// if (!valid) {
///     throw new IllegalStateException("XML 文件校验失败");
/// ```
///
/// @author linqibin
/// @since 0.1.0
public interface MeshFileDownloadPort {

  /// 下载 MeSH XML 文件。
  ///
  /// 实现说明：
  ///
  /// - 从 NLM 服务器下载 XML 文件（约 299MB）
  ///   - 支持断点续传（使用 HTTP Range 请求）
  ///   - 失败时自动重试（最多 3 次）
  ///   - 下载到临时目录：`/tmp/mesh-import/{filename}.xml`
  ///   - 记录下载进度日志
  ///
  /// @param sourceUrl 数据源 URL（如
  // https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc{year}.xml）
  /// @return 下载后的本地文件
  /// @throws IllegalArgumentException 如果 URL 无效
  /// @throws RuntimeException 如果下载失败（网络异常、IO 异常等）
  File download(String sourceUrl);

  /// 验证 XML 文件校验和（MD5 哈希）。
  ///
  /// 实现说明：
  ///
  /// - 计算文件的 MD5 哈希值
  ///   - 与预期哈希值比较（忽略大小写）
  ///   - 使用 DigestUtils 或 Hutool 工具计算 MD5
  ///
  /// @param xmlFile XML 文件
  /// @param expectedHash 预期的 MD5 哈希值（32 位十六进制字符串）
  /// @return true 如果校验和匹配
  /// @throws IllegalArgumentException 如果文件不存在或哈希格式无效
  /// @throws RuntimeException 如果计算哈希失败（IO 异常）
  boolean validateChecksum(File xmlFile, String expectedHash);
}
