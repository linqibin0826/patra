package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.exception.SerfileParseException;
import com.patra.catalog.domain.model.dto.serfile.SerialRecord;
import com.patra.catalog.domain.port.parser.SerfileParserPort;
import com.patra.catalog.infra.adapter.parser.strategy.SerialParsingStrategy;
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

/// NLM Serfile XML 解析器实现。
///
/// 使用 StAX（Streaming API for XML）实现流式解析 NLM Serfile 文件。
/// 支持处理约 35,000 条期刊记录的大型 XML 文件。
///
/// **设计原则**：
///
/// - **策略模式**：委托 {@link SerialParsingStrategy} 处理单条记录解析
/// - **流式处理**：使用 {@link XMLStreamReader} 逐元素读取，内存占用可控
/// - **惰性求值**：返回 {@link Stream}，由调用方控制处理速度
/// - **资源管理**：Stream 关闭时自动释放底层资源
/// - **安全配置**：禁用外部实体引用，防止 XXE 攻击
///
/// **性能特征**：
///
/// - 内存占用：<1GB（流式处理，不一次性加载整个 XML）
/// - 处理速度：约 1000 条/秒
/// - 文件大小：支持 SerfileBase 2025（约 35,000 条记录）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class SerfileParserAdapter implements SerfileParserPort {

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
  public Stream<SerialRecord> parseSerials(Path filePath) {
    log.info("开始解析 NLM Serfile XML 文件：{}", filePath);

    try {
      InputStream inputStream = Files.newInputStream(filePath);
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(inputStream);
      XmlParsingContext context = XmlParsingContext.empty();
      var spliterator = new RecordSpliterator<>(reader, SerialParsingStrategy.INSTANCE, context);

      return StreamSupport.stream(spliterator, false)
          .onClose(() -> closeReader(reader))
          .onClose(() -> closeInputStream(inputStream));
    } catch (IOException e) {
      log.error("打开 Serfile 文件失败：{}", filePath, e);
      throw new SerfileParseException("打开 Serfile XML 文件失败：" + filePath, e);
    } catch (XMLStreamException e) {
      log.error("创建 XML 读取器失败：{}", filePath, e);
      throw new SerfileParseException("Serfile XML 解析失败：" + filePath, e);
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
