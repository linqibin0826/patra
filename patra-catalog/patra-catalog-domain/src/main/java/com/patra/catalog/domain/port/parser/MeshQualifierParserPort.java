package com.patra.catalog.domain.port.parser;

import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import java.nio.file.Path;
import java.util.stream.Stream;

/// MeSH 限定词解析端口（领域层定义，基础设施层实现）。
///
/// 从 MeSH Qualifier XML 文件中流式解析限定词聚合根。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立于具体解析技术
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 使用 Stream 返回，支持大文件流式处理
///
/// **主要使用场景**：
///
/// MeSH 限定词导入，解析约 80 条限定词记录。限定词是主数据，必须先于主题词导入。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshQualifierParserPort {

  /// 解析 MeSH 限定词文件，返回聚合根流。
  ///
  /// 使用 StAX 流式解析，避免将整个文件加载到内存。
  /// 调用方负责关闭返回的 Stream（推荐使用 try-with-resources）。
  ///
  /// **使用示例**：
  ///
  /// ```java
  /// try (Stream<MeshQualifierAggregate> stream = port.parse(filePath, "2025")) {
  ///     stream.forEach(qualifier -> processQualifier(qualifier));
  /// }
  /// ```
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本号（如 "2025"）
  /// @return 限定词聚合根流（调用方负责关闭）
  /// @throws com.patra.catalog.domain.exception.MeshParseException 解析失败时抛出
  Stream<MeshQualifierAggregate> parse(Path filePath, String meshVersion);
}
