package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.dto.serfile.SerialRecord;
import java.nio.file.Path;
import java.util.stream.Stream;

/// NLM Serfile XML 解析端口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 使用 Stream 返回，支持大文件流式处理
///
/// **主要使用场景**：
///
/// NLM SerfileBase 数据导入，解析约 35,000 条期刊记录
///
/// @author linqibin
/// @since 0.1.0
public interface SerfileParserPort {

  /// 解析 Serfile XML，返回 Serial 记录流。
  ///
  /// 使用 StAX 流式解析，避免将整个文件加载到内存。
  /// 调用方负责关闭返回的 Stream（推荐使用 try-with-resources）。
  ///
  /// **使用示例**：
  ///
  /// ```java
  /// try (Stream<SerialRecord> stream = serfileParserPort.parseSerials(filePath)) {
  ///     stream.forEach(record -> processRecord(record));
  /// }
  /// ```
  ///
  /// @param filePath XML 文件路径
  /// @return Serial 记录流（调用方负责关闭）
  /// @throws com.patra.catalog.domain.exception.SerfileParseException 解析失败时抛出
  Stream<SerialRecord> parseSerials(Path filePath);
}
