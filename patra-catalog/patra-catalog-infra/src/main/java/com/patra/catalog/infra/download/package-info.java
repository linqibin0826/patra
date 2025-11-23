/// 文件下载适配器包。
///
/// 实现 Domain 层 {@link com.patra.catalog.domain.port.MeshFileDownloadPort} 端口，提供文件下载能力。
///
/// ## 职责
///
/// - **HTTP 下载**：使用 Spring RestClient（底层 JDK 21 HttpClient）下载 MeSH XML 文件
///   - **流式写入**：支持大文件（299MB）流式下载，避免内存溢出
///   - **完整性校验**：使用 MD5 哈希验证文件完整性
///   - **临时文件管理**：下载到临时目录，导入完成后清理
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.infra.download.RestClientMeshFileDownloadImpl} - RestClient 文件下载器实现
///
/// - 下载 NLM 官方 MeSH XML 文件（desc2025.xml、qual2025.xml）
///       - 流式写入到临时目录（/tmp/mesh-import）
///       - 计算 MD5 哈希验证文件完整性
///       - 返回本地临时文件路径供后续解析使用
///
/// ## 设计原则
///
/// - **流式处理**：使用 InputStream + FileOutputStream 流式复制，内存占用可控（<100MB）
///   - **超时控制**：通过 RestClient 配置连接超时和读取超时（默认 10 分钟）
///   - **错误处理**：HTTP 错误、网络超时、磁盘写入失败均抛出异常
///   - **资源清理**：使用 try-with-resources 确保流的关闭
///
/// ## 性能特征
///
/// | 指标 | 值 | 说明 |
/// |------|---|------|
/// | 文件大小 | 299 MB | MeSH Descriptor XML |
/// | 下载速度 | 10-50 MB/s | 取决于网络带宽 |
/// | 内存占用 | <100 MB | 流式写入，不全部加载到内存 |
/// | 超时时间 | 10 分钟 | 可配置 |
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：下载 MeSH XML 文件
/// @Service
/// @RequiredArgsConstructor
/// public class MeshImportOrchestrator {
///
///     private final MeshFileDownloadPort downloadPort;
///
///     public void downloadXml() {
///         String url = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";
///
///         // 下载文件（流式下载到临时目录）
///         File xmlFile = downloadPort.download(url);
///
///         log.info("文件下载成功：{}", xmlFile.getAbsolutePath());
///         log.info("文件大小：{} MB", xmlFile.length() / 1024 / 1024);
///
///         // 使用文件...
///     }
/// }
///
/// // 示例 2：验证文件完整性
/// File xmlFile = downloadPort.download(url);
/// String md5Hash = downloadPort.calculateMd5(xmlFile);
/// log.info("MD5 哈希：{}", md5Hash);
///
/// // 示例 3：异常处理
/// try {
///     File xmlFile = downloadPort.download(invalidUrl);
/// } catch (RuntimeException e) {
///     // 下载失败：HTTP 404 (Not Found)
///     // 下载失败：网络连接超时
///     // 磁盘写入失败: No space left on device
///     log.error("文件下载失败", e);
/// }
/// ```
///
/// ## 临时文件管理
///
/// - **下载目录**：`System.getProperty("java.io.tmpdir") + "/mesh-import"`（如 `/tmp/mesh-import`）
/// - **文件命名**：从 URL 提取文件名（如 `desc2025.xml`）
/// - **清理策略**：导入完成后由 Orchestrator 手动删除临时文件
///
/// ## 错误处理
///
/// | 异常场景 | 异常类型 | 错误消息示例 |
/// |---------|---------|-------------|
/// | URL 格式错误 | IllegalArgumentException | "URL 格式错误，必须以 http:// 或 https:// 开头" |
/// | HTTP 错误 | RuntimeException | "下载失败: HTTP 404 (Not Found)" |
/// | 网络超时 | RuntimeException | "网络连接超时" |
/// | 磁盘写入失败 | RuntimeException | "磁盘写入失败: No space left on device" |
///
/// ## 架构位置
///
/// **Infrastructure 层 - 出站适配器**：
///
/// - 六边形架构的出站适配器（Outbound Adapter）
/// - 实现 Domain 层定义的 Port 接口
/// - 封装外部系统交互细节（HTTP、文件系统）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.infra.download;
