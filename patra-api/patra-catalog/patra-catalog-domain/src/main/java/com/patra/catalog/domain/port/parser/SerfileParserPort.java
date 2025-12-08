package com.patra.catalog.domain.port.parser;

import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import java.io.InputStream;
import java.util.stream.Stream;

/// NLM Serfile XML 解析端口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 使用 Stream 返回，支持大文件流式处理
/// - 返回领域层值对象，而非 Infra 层 DTO
///
/// **主要使用场景**：
///
/// NLM SerfileBase 数据导入，解析约 35,000 条期刊记录
///
/// @author linqibin
/// @since 0.1.0
public interface SerfileParserPort {

  /// 解析 Serfile XML 输入流，返回 PubMed 期刊数据流。
  ///
  /// 使用 StAX 流式解析，避免将整个文件加载到内存。
  /// 调用方负责关闭返回的 Stream（推荐使用 try-with-resources）。
  ///
  /// **注意**：此方法**不关闭**传入的 InputStream，由调用方负责管理。
  ///
  /// **使用示例**：
  ///
  /// ```java
  /// try (StreamingDownloadResult result = downloadPort.download(uri)) {
  ///     result.inputStream().forEach(data -> processData(data));
  /// }
  /// ```
  ///
  /// @param inputStream XML 输入流（调用方负责关闭）
  /// @return PubMed 期刊数据流（调用方负责关闭）
  /// @throws com.patra.catalog.domain.exception.XmlParseException 解析失败时抛出
  Stream<PubmedSerialData> parse(InputStream inputStream);
}
