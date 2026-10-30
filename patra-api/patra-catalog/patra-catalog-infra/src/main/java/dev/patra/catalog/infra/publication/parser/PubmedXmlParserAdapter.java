package dev.patra.catalog.infra.publication.parser;

import dev.patra.catalog.domain.kernel.exception.XmlParseException;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication;
import dev.patra.catalog.domain.publication.port.parser.PubmedXmlParserPort;
import dev.patra.catalog.infra.kernel.support.SecureXmlInputFactory;
import dev.patra.catalog.infra.kernel.support.XmlParsingContext;
import java.io.StringReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/// PubMed XML 解析适配器：在独立的 `XMLStreamReader` 上用 `CanonicalPublicationParsingStrategy` 解析一篇原文。
///
/// @author linqibin
/// @since 0.1.0
@Component
public class PubmedXmlParserAdapter implements PubmedXmlParserPort {

  /// 解析 PubMed XML 输入流。
  ///
  /// @param inputStream XML 输入流（调用方负责关闭）
  /// @return 文献记录流（惰性求值）
  /// @throws XmlParseException 解析失败时抛出
  @Override
  public CanonicalPublication parseArticle(String rawXml) {
    XMLStreamReader reader = null;
    try {
      reader = SecureXmlInputFactory.getInstance().createXMLStreamReader(new StringReader(rawXml));
      while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) {
        // 定位到 <PubmedArticle>
      }
      return CanonicalPublicationParsingStrategy.INSTANCE.parseRecord(
          reader, XmlParsingContext.empty());
    } catch (XMLStreamException e) {
      throw new XmlParseException("PubmedArticle 原文解析失败", e);
    } finally {
      closeQuietly(reader);
    }
  }

  /// 关闭读取器，失败忽略（原文在内存里，没有外部资源）。
  ///
  /// @param reader 读取器，可空
  private static void closeQuietly(XMLStreamReader reader) {
    if (reader == null) {
      return;
    }
    try {
      reader.close();
    } catch (XMLStreamException ignored) {
      // 内存里的字符串，关闭失败不影响结果
    }
  }
}
