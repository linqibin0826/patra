package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.port.XmlParserPort;
import com.patra.catalog.infra.adapter.parser.strategy.ConceptParsingStrategy;
import com.patra.catalog.infra.adapter.parser.strategy.DescriptorParsingStrategy;
import com.patra.catalog.infra.adapter.parser.strategy.EntryTermParsingStrategy;
import com.patra.catalog.infra.adapter.parser.strategy.QualifierParsingStrategy;
import com.patra.catalog.infra.adapter.parser.strategy.RecordParsingStrategy;
import com.patra.catalog.infra.adapter.parser.strategy.TreeNumberParsingStrategy;
import com.patra.catalog.infra.adapter.parser.support.RecordSpliterator;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// StAX XML 解析器实现（门面类）。
///
/// 使用 JDK 内置的 StAX（Streaming API for XML）实现流式解析，支持大文件处理。
/// 通过策略模式委托具体解析逻辑，保持门面接口简洁。
///
/// **设计原则**：
/// - **策略模式**：各记录类型（Descriptor、Qualifier、Concept 等）由独立策略处理
/// - **流式处理**：使用 {@link XMLStreamReader} 逐元素读取，内存占用可控（<2GB）
/// - **惰性求值**：返回 {@link Stream}，由调用方控制处理速度
/// - **资源管理**：使用 Spliterator 封装，确保流关闭时释放资源
/// - **错误处理**：格式错误时跳过记录并记录日志，不中断整个流
///
/// **性能特征**：
/// - 内存占用：<2GB（流式处理，不一次性加载整个 XML）
/// - 处理速度：约 1000 条/秒（取决于硬件和批次大小）
/// - 文件大小：支持 299MB 的 XML 文件
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class XmlParserAdapter implements XmlParserPort {

  private static final XMLInputFactory XML_INPUT_FACTORY;

  static {
    XML_INPUT_FACTORY = XMLInputFactory.newInstance();
    // 禁用外部实体引用，防止 XXE 攻击（OWASP A03:2021 Injection）
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    // 设置空的 XMLResolver，忽略所有外部 DTD/实体引用（避免 DOCTYPE 解析错误）
    XML_INPUT_FACTORY.setXMLResolver((publicID, systemID, baseURI, namespace) -> null);
  }

  @Override
  public Stream<MeshDescriptorAggregate> parseDescriptors(
      InputStream xmlInputStream, String meshVersion) {
    log.info("开始解析 MeSH Descriptor XML 文件，版本：{}", meshVersion);
    return createStream(
        xmlInputStream, DescriptorParsingStrategy.INSTANCE, XmlParsingContext.of(meshVersion));
  }

  @Override
  public Stream<MeshQualifierAggregate> parseQualifiers(Path filePath, String meshVersion) {
    log.info("开始解析 MeSH Qualifier XML 文件：{}，版本：{}", filePath, meshVersion);

    try {
      InputStream inputStream = Files.newInputStream(filePath);
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(inputStream);
      XmlParsingContext context = XmlParsingContext.of(meshVersion);
      var spliterator = new RecordSpliterator<>(reader, QualifierParsingStrategy.INSTANCE, context);

      return StreamSupport.stream(spliterator, false)
          .onClose(() -> closeReader(reader))
          .onClose(() -> closeInputStream(inputStream));
    } catch (IOException e) {
      log.error("打开文件失败：{}", filePath, e);
      throw new XmlParsingException("打开 XML 文件失败：" + filePath, e);
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw new XmlParsingException("XML 解析失败", e);
    }
  }

  @Override
  public Stream<MeshTreeNumber> parseTreeNumbers(InputStream xmlInputStream) {
    log.info("开始解析 MeSH TreeNumber XML 文件");
    return createStream(
        xmlInputStream, TreeNumberParsingStrategy.INSTANCE, XmlParsingContext.empty());
  }

  @Override
  public Stream<MeshEntryTerm> parseEntryTerms(InputStream xmlInputStream) {
    log.info("开始解析 MeSH EntryTerm XML 文件");
    return createStream(
        xmlInputStream, EntryTermParsingStrategy.INSTANCE, XmlParsingContext.empty());
  }

  @Override
  public Stream<MeshConcept> parseConcepts(InputStream xmlInputStream) {
    log.info("开始解析 MeSH Concept XML 文件");
    return createStream(xmlInputStream, ConceptParsingStrategy.INSTANCE, XmlParsingContext.empty());
  }

  // ========== 私有方法 ==========

  /// 创建解析流。
  ///
  /// 通用方法：创建 XMLStreamReader，配合 RecordSpliterator 生成 Stream。
  ///
  /// @param inputStream XML 输入流
  /// @param strategy 解析策略
  /// @param context 解析上下文
  /// @param <T> 记录类型
  /// @return 惰性求值的记录流
  private <T> Stream<T> createStream(
      InputStream inputStream, RecordParsingStrategy<T> strategy, XmlParsingContext context) {
    try {
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(inputStream);
      var spliterator = new RecordSpliterator<>(reader, strategy, context);
      return StreamSupport.stream(spliterator, false).onClose(() -> closeReader(reader));
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败", e);
      throw new XmlParsingException("XML 解析失败", e);
    }
  }

  /// 关闭 XMLStreamReader。
  private void closeReader(XMLStreamReader reader) {
    try {
      reader.close();
      log.debug("XMLStreamReader 已关闭");
    } catch (XMLStreamException e) {
      log.warn("关闭 XMLStreamReader 失败", e);
    }
  }

  /// 关闭输入流。
  private void closeInputStream(InputStream inputStream) {
    try {
      inputStream.close();
      log.debug("InputStream 已关闭");
    } catch (IOException e) {
      log.warn("关闭 InputStream 失败", e);
    }
  }
}
