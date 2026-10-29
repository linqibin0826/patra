package dev.patra.catalog.infra.publication.batch;

import dev.patra.catalog.domain.kernel.exception.XmlParseException;
import dev.patra.catalog.infra.kernel.support.SecureXmlInputFactory;
import dev.patra.catalog.infra.publication.parser.PubmedXmlElements;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/// 把一个 PubMed XML 文件逐篇切成 `<PubmedArticle>` 原文，顺带收集 `<DeleteCitation>` 里的 PMID。
///
/// 每篇原文由 StAX 事件复制而成，与原文件语义等价，不保证逐字节相同（空白与转义会被规范化）；
/// 打开命名空间修复，篇内用到、在根元素上声明的前缀（如 `mml`）会补声明，切出的原文可以单独解析。
///
/// @author linqibin
/// @since 0.1.0
public final class PubmedArticleSplitter implements AutoCloseable {

  /// 篇内第一个 `<PMID>`（即 `MedlineCitation/PMID`）。
  private static final Pattern FIRST_PMID = Pattern.compile("<PMID[^>]*>\\s*(\\d+)\\s*</PMID>");

  private static final XMLOutputFactory OUTPUT_FACTORY = newOutputFactory();

  private final XMLEventReader reader;
  private final Consumer<String> deletedPmidSink;

  /// 创建切分器。
  ///
  /// @param xml 解压后的 XML 流（调用方负责关闭）
  /// @param deletedPmidSink 每遇到 `DeleteCitation` 里的一个 PMID 调用一次
  /// @throws XmlParseException 创建 XML 读取器失败时
  public PubmedArticleSplitter(InputStream xml, Consumer<String> deletedPmidSink) {
    try {
      this.reader = SecureXmlInputFactory.getInstance().createXMLEventReader(xml);
    } catch (XMLStreamException e) {
      throw new XmlParseException("创建 XML 读取器失败", e);
    }
    this.deletedPmidSink = deletedPmidSink;
  }

  /// 读到下一篇 `<PubmedArticle>`。
  ///
  /// @return 这篇的原文；读完时为空
  /// @throws XmlParseException XML 不完整或格式错误时（例如下载被截断）
  public Optional<String> next() {
    try {
      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (!event.isStartElement()) {
          continue;
        }
        String name = event.asStartElement().getName().getLocalPart();
        if (PubmedXmlElements.Container.PUBMED_ARTICLE.equals(name)) {
          return Optional.of(copySubtree(event.asStartElement()));
        }
        if (PubmedXmlElements.Container.DELETE_CITATION.equals(name)) {
          readDeletedPmids();
        }
      }
      return Optional.empty();
    } catch (XMLStreamException e) {
      throw new XmlParseException("XML 解析失败", e);
    }
  }

  /// 从一篇原文里取 `MedlineCitation/PMID`（篇内第一个 `<PMID>`），不做完整解析。
  ///
  /// @param rawXml 一篇 `<PubmedArticle>` 原文
  /// @return PMID；找不到时为 null
  public static String pmidOf(String rawXml) {
    Matcher m = FIRST_PMID.matcher(rawXml);
    return m.find() ? m.group(1) : null;
  }

  /// 关闭事件读取器（不关闭底层流）。
  ///
  /// @throws XMLStreamException 关闭失败时
  @Override
  public void close() throws XMLStreamException {
    reader.close();
  }

  /// 从给定的开始标签起，把整个子树复制成字符串。
  ///
  /// @param start 子树的开始标签
  /// @return 子树原文
  /// @throws XMLStreamException 读写失败时
  private String copySubtree(StartElement start) throws XMLStreamException {
    StringWriter out = new StringWriter();
    XMLEventWriter writer = OUTPUT_FACTORY.createXMLEventWriter(out);
    writer.add(start);
    int depth = 1;
    while (depth > 0) {
      XMLEvent event = reader.nextEvent();
      if (event.isStartElement()) {
        depth++;
      } else if (event.isEndElement()) {
        depth--;
      }
      writer.add(event);
    }
    writer.flush();
    writer.close();
    return out.toString();
  }

  /// 读完一个 `<DeleteCitation>`，把其中每个 PMID 交给回调。
  ///
  /// @throws XMLStreamException 读失败时
  private void readDeletedPmids() throws XMLStreamException {
    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();
      if (event.isStartElement()
          && PubmedXmlElements.Identifier.PMID.equals(
              event.asStartElement().getName().getLocalPart())) {
        String pmid = reader.getElementText().trim();
        if (!pmid.isEmpty()) {
          deletedPmidSink.accept(pmid);
        }
      } else if (event.isEndElement()
          && PubmedXmlElements.Container.DELETE_CITATION.equals(
              event.asEndElement().getName().getLocalPart())) {
        return;
      }
    }
  }

  /// 输出工厂：打开命名空间修复。
  ///
  /// @return 输出工厂
  private static XMLOutputFactory newOutputFactory() {
    XMLOutputFactory factory = XMLOutputFactory.newInstance();
    factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    return factory;
  }
}
