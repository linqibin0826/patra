package dev.linqibin.patra.catalog.domain.port.parser;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import java.io.InputStream;
import java.util.stream.Stream;

/// MeSH 补充概念记录（SCR）解析端口（领域层定义，基础设施层实现）。
///
/// 从 MeSH Supplemental Record XML 文件中流式解析 SCR 聚合根。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立于具体解析技术
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 使用 Stream 返回，支持大文件流式处理（约 350,000 条记录）
///
/// **主要使用场景**：
///
/// MeSH SCR 批量导入，通过 Spring Batch 逐条消费 Stream。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshScrParserPort {

  /// 解析 MeSH SCR 输入流，返回聚合根流。
  ///
  /// 使用 StAX 流式解析，避免将整个文件加载到内存。
  /// 调用方负责关闭返回的 Stream（推荐使用 try-with-resources）。
  ///
  /// **注意**：
  ///
  /// - 返回的聚合根不包含 meshVersion，调用方需通过 `withMeshVersion()` 设置
  /// - 此方法**不关闭**传入的 InputStream，由调用方负责管理
  ///
  /// **使用示例**：
  ///
  /// ```java
  /// FileDownloadResult result = downloadPort.download(uri);
  /// try (InputStream in = Files.newInputStream(result.filePath())) {
  ///     port.parse(in)
  ///         .map(scr -> scr.withMeshVersion("2025"))
  ///         .forEach(scr -> processScr(scr));
  /// } finally {
  ///     Files.deleteIfExists(result.filePath());
  /// }
  /// ```
  ///
  /// @param inputStream XML 输入流（调用方负责关闭）
  /// @return SCR 聚合根流（调用方负责关闭）
  /// @throws dev.linqibin.patra.catalog.domain.exception.XmlParseException 解析失败时抛出
  Stream<MeshScrAggregate> parse(InputStream inputStream);
}
