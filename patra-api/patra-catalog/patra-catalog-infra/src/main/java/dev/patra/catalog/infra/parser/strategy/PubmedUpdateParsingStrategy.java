package dev.patra.catalog.infra.parser.strategy;

import dev.patra.catalog.infra.parser.PubmedXmlElements;
import dev.patra.catalog.infra.parser.support.XmlParsingContext;
import dev.patra.catalog.infra.parser.support.XmlParsingHelper;
import dev.patra.common.CanonicalPublication;
import java.util.function.Consumer;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// PubMed updatefiles 的记录解析策略。
///
/// updatefiles 比 baseline 多一种顶层元素 `<DeleteCitation>`，里面是 NLM 撤下的 PMID 列表。
/// 本策略对 `<PubmedArticle>` 委托 `CanonicalPublicationParsingStrategy` 解析，
/// 对 `<DeleteCitation>` 逐个把 `PMID` 文本交给构造时传入的回调，并返回 `null`
/// （`AbstractRecordSpliterator` 对 `null` 继续查找下一条记录）。
///
/// @author linqibin
/// @since 0.11.0
public final class PubmedUpdateParsingStrategy
    implements RecordParsingStrategy<CanonicalPublication> {

  private final Consumer<String> deletedPmidSink;

  /// 创建更新文件解析策略。
  ///
  /// @param deletedPmidSink 每遇到一个被删除的 PMID 调用一次
  public PubmedUpdateParsingStrategy(Consumer<String> deletedPmidSink) {
    this.deletedPmidSink = deletedPmidSink;
  }

  /// 返回记录根元素名 `PubmedArticle`。
  ///
  /// @return 根元素名
  @Override
  public String rootElementName() {
    return PubmedXmlElements.Container.PUBMED_ARTICLE;
  }

  /// 同时匹配 `PubmedArticle` 与 `DeleteCitation`。
  ///
  /// @param reader 定位在起始元素上的读取器
  /// @return 是否由本策略处理
  @Override
  public boolean matches(XMLStreamReader reader) {
    String localName = reader.getLocalName();
    return PubmedXmlElements.Container.PUBMED_ARTICLE.equals(localName)
        || PubmedXmlElements.Container.DELETE_CITATION.equals(localName);
  }

  /// 解析一条记录：文章返回 `CanonicalPublication`，删除列表交给回调后返回 `null`。
  ///
  /// @param reader 定位在起始元素上的读取器
  /// @param context 解析上下文
  /// @return 文章记录，`DeleteCitation` 时为 `null`
  /// @throws XMLStreamException 读取失败时抛出
  @Override
  public CanonicalPublication parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    if (PubmedXmlElements.Container.DELETE_CITATION.equals(reader.getLocalName())) {
      readDeletedPmids(reader);
      return null;
    }
    return CanonicalPublicationParsingStrategy.INSTANCE.parseRecord(reader, context);
  }

  /// 读完 `DeleteCitation` 内的所有 `PMID`，逐个交给回调。
  private void readDeletedPmids(XMLStreamReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Identifier.PMID.equals(reader.getLocalName())) {
        String pmid = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
        if (!pmid.isEmpty()) {
          deletedPmidSink.accept(pmid);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Container.DELETE_CITATION.equals(reader.getLocalName())) {
        return;
      }
    }
  }
}
