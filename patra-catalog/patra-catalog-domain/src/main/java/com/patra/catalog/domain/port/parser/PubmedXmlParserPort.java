package com.patra.catalog.domain.port.parser;

import com.patra.catalog.domain.model.vo.publication.pubmed.PubmedArticle;
import java.io.InputStream;
import java.util.stream.Stream;

/// PubMed 文献 XML 解析端口（领域层定义，基础设施层实现）。
///
/// 从 PubMed Baseline XML 文件中流式解析文献记录。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立于具体解析技术
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 使用 Stream 返回，支持大文件流式处理（约 30,000 条/文件）
///
/// **返回类型说明**：
///
/// 返回 `PubmedArticle` 中间 DTO 而非 `PublicationAggregate`，原因：
///
/// 1. **解耦解析与业务逻辑**：解析器只负责提取 XML 数据
/// 2. **Venue 匹配延迟**：需要在 Processor 阶段进行 Venue 匹配
/// 3. **批处理管道**：Reader → Processor → Writer 职责分离
///
/// **主要使用场景**：
///
/// PubMed Baseline 文献批量导入，通过 Spring Batch 逐条消费 Stream。
///
/// @author linqibin
/// @since 0.1.0
public interface PubmedXmlParserPort {

  /// 解析 PubMed XML 输入流，返回文献记录流。
  ///
  /// 使用 StAX 流式解析，避免将整个文件加载到内存。
  /// 调用方负责关闭返回的 Stream（推荐使用 try-with-resources）。
  ///
  /// **注意**：
  ///
  /// - 此方法**不关闭**传入的 InputStream，由调用方负责管理
  /// - 每个 `PubmedArticle` 对应一个 `<PubmedArticle>` XML 元素
  /// - 无效记录（缺少 PMID）会被跳过，不会中断流处理
  ///
  /// **使用示例**：
  ///
  /// ```java
  /// try (StreamingDownloadResult result = downloadPort.download(uri)) {
  ///     port.parse(result.inputStream())
  ///         .forEach(article -> processArticle(article));
  /// }
  /// ```
  ///
  /// @param inputStream XML 输入流（调用方负责关闭）
  /// @return 文献记录流（调用方负责关闭）
  /// @throws com.patra.catalog.domain.exception.XmlParseException 解析失败时抛出
  Stream<PubmedArticle> parse(InputStream inputStream);
}
