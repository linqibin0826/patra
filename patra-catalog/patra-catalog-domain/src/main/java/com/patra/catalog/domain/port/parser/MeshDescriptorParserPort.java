package com.patra.catalog.domain.port.parser;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import java.io.InputStream;
import java.util.stream.Stream;

/// MeSH 主题词解析端口（领域层定义，基础设施层实现）。
///
/// 从 MeSH Descriptor XML 文件中流式解析主题词聚合根。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立于具体解析技术
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 使用 Stream 返回，支持大文件流式处理（约 35,000 条记录）
///
/// **主要使用场景**：
///
/// MeSH 主题词批量导入，通过 Spring Batch 逐条消费 Stream。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorParserPort {

  /// 解析 MeSH 主题词输入流，返回聚合根流。
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
  /// try (StreamingDownloadResult result = downloadPort.download(uri)) {
  ///     port.parse(result.inputStream())
  ///         .map(d -> d.withMeshVersion("2025"))
  ///         .forEach(descriptor -> processDescriptor(descriptor));
  /// }
  /// ```
  ///
  /// @param inputStream XML 输入流（调用方负责关闭）
  /// @return 主题词聚合根流（调用方负责关闭）
  /// @throws com.patra.catalog.domain.exception.XmlParseException 解析失败时抛出
  Stream<MeshDescriptorAggregate> parse(InputStream inputStream);
}
