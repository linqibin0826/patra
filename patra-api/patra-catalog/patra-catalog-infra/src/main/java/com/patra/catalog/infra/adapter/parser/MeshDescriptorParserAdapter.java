package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.port.parser.MeshDescriptorParserPort;
import com.patra.catalog.infra.adapter.parser.strategy.DescriptorParsingStrategy;
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

/// MeSH 主题词 XML 解析适配器。
///
/// 使用 StAX 流式解析 MeSH Descriptor XML 文件。
///
/// **设计原则**：
///
/// - **单一职责**：只负责主题词解析，与限定词解析分离
/// - **资源管理**：内部管理 InputStream 和 XMLStreamReader 生命周期
/// - **流式处理**：使用 Stream 返回，支持大文件惰性求值
///
/// **性能特征**：
///
/// - 内存占用：<2GB（流式处理，不一次性加载整个 XML）
/// - 处理速度：约 1000 条/秒
/// - 数据量：约 35,000 条主题词
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class MeshDescriptorParserAdapter implements MeshDescriptorParserPort {

  private static final XMLInputFactory XML_INPUT_FACTORY;

  static {
    XML_INPUT_FACTORY = XMLInputFactory.newInstance();
    // 禁用外部实体引用，防止 XXE 攻击（OWASP A03:2021 Injection）
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    // 设置空的 XMLResolver，忽略所有外部 DTD/实体引用
    XML_INPUT_FACTORY.setXMLResolver((publicID, systemID, baseURI, namespace) -> null);
  }

  @Override
  public Stream<MeshDescriptorAggregate> parse(Path filePath, String meshVersion) {
    log.info("开始解析 MeSH Descriptor XML 文件：{}，版本：{}", filePath, meshVersion);

    try {
      InputStream inputStream = Files.newInputStream(filePath);
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(inputStream);
      XmlParsingContext context = XmlParsingContext.of(meshVersion);
      var spliterator =
          new RecordSpliterator<>(reader, DescriptorParsingStrategy.INSTANCE, context);

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
