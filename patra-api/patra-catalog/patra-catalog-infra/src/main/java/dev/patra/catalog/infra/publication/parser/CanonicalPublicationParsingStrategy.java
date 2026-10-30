package dev.patra.catalog.infra.publication.parser;

import dev.patra.catalog.domain.kernel.model.enums.PublicationIdentifierType;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Abstract;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.AbstractSection;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Affiliation;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Author;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.DescriptorName;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Identifier;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Journal;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Keyword;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.KeywordSet;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.MeshHeading;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Pagination;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.PublicationDates;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.PublicationType;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.QualifierName;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.SupplMeshName;
import dev.patra.catalog.domain.publication.model.vo.PartialDate;
import dev.patra.catalog.infra.kernel.support.RecordParsingStrategy;
import dev.patra.catalog.infra.kernel.support.XmlParsingContext;
import dev.patra.catalog.infra.kernel.support.XmlParsingHelper;
import dev.patra.catalog.infra.publication.parser.RunInAbstractLabelSplitter.LabeledText;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// CanonicalPublication 解析策略。
///
/// 解析 PubMed Baseline XML 中的 `<PubmedArticle>` 元素，直接创建 `CanonicalPublication`。
///
/// **XML 结构**：
///
/// ```xml
/// <PubmedArticle>
///   <MedlineCitation Status="MEDLINE">
///     <PMID Version="1">12345678</PMID>
///     <Article PubModel="Print">
///       <Journal>
///         <ISSN IssnType="Print">1234-5678</ISSN>
///         <JournalIssue>...</JournalIssue>
///         <Title>Journal Name</Title>
///       </Journal>
///       <ArticleTitle>...</ArticleTitle>
///       <VernacularTitle>...</VernacularTitle>
///       <Language>eng</Language>
///       <AuthorList CompleteYN="Y">...</AuthorList>
///     </Article>
///     <MedlineJournalInfo>
///       <NlmUniqueID>101234567</NlmUniqueID>
///       <ISSNLinking>1111-2222</ISSNLinking>
///     </MedlineJournalInfo>
///   </MedlineCitation>
///   <PubmedData>
///     <PublicationStatus>epublish</PublicationStatus>
///     <ArticleIdList>...</ArticleIdList>
///   </PubmedData>
/// </PubmedArticle>
/// ```
///
/// **设计说明**：
///
/// - 本策略直接构建 `CanonicalPublication`（Shared Kernel 标准化模型）
/// - 字段映射遵循 PubMed DTD → CanonicalPublication 的标准转换规则
/// - 月份支持数字和英文缩写两种格式
///
/// **关键决策**：
///
/// 1. 多语言处理：取第一个语言，保留原始 ISO 639-3 代码（转换在 Processor 层完成）
/// 2. ISSN 存储：优先存储 Print ISSN，issnLinking 单独存储
/// 3. 日期转换：Year/Month/Day → [PartialDate]，按实际精度记录，缺失的月/日不补
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class CanonicalPublicationParsingStrategy
    implements RecordParsingStrategy<CanonicalPublication> {

  /// 单例实例。
  public static final CanonicalPublicationParsingStrategy INSTANCE =
      new CanonicalPublicationParsingStrategy();

  /// 从 MedlineDate 提取年份的正则表达式。
  private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

  /// 月份缩写到数字的映射。
  private static final Map<String, Integer> MONTH_MAP =
      Map.ofEntries(
          Map.entry("jan", 1),
          Map.entry("feb", 2),
          Map.entry("mar", 3),
          Map.entry("apr", 4),
          Map.entry("may", 5),
          Map.entry("jun", 6),
          Map.entry("jul", 7),
          Map.entry("aug", 8),
          Map.entry("sep", 9),
          Map.entry("oct", 10),
          Map.entry("nov", 11),
          Map.entry("dec", 12));

  private CanonicalPublicationParsingStrategy() {}

  @Override
  public String rootElementName() {
    return PubmedXmlElements.Container.PUBMED_ARTICLE;
  }

  /// 解析单个 PubmedArticle 元素。
  ///
  /// @param reader XML 流读取器（已定位到 PubmedArticle 元素）
  /// @param context 解析上下文（当前未使用）
  /// @return CanonicalPublication，缺少必填字段（PMID）时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public CanonicalPublication parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    ParsedFields fields = new ParsedFields();

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        parseStartElement(reader, fields);
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Container.PUBMED_ARTICLE.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段
    if (fields.pmid == null) {
      log.warn("跳过无效 PubmedArticle（缺少 PMID）");
      return null;
    }

    return buildPublication(fields);
  }

  // ========== 元素解析 ==========

  /// 解析起始元素。
  private void parseStartElement(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    String localName = reader.getLocalName();

    switch (localName) {
      case PubmedXmlElements.Container.MEDLINE_CITATION -> parseMedlineCitation(reader, fields);
      case PubmedXmlElements.Container.PUBMED_DATA -> parsePubmedData(reader, fields);
      default -> XmlParsingHelper.skipElement(reader, localName);
    }
  }

  /// 解析 MedlineCitation 元素。
  private void parseMedlineCitation(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    fields.citationStatus = reader.getAttributeValue(null, PubmedXmlElements.Attribute.STATUS);
    fields.citationOwner = reader.getAttributeValue(null, PubmedXmlElements.Attribute.OWNER);
    fields.indexingMethod =
        reader.getAttributeValue(null, PubmedXmlElements.Attribute.INDEXING_METHOD);

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Identifier.PMID ->
              fields.pmid = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Container.ARTICLE -> parseArticle(reader, fields);
          case PubmedXmlElements.Journal.MEDLINE_JOURNAL_INFO ->
              parseMedlineJournalInfo(reader, fields);
          case PubmedXmlElements.MeSH.MESH_HEADING_LIST -> parseMeshHeadingList(reader, fields);
          case PubmedXmlElements.SupplMesh.SUPPL_MESH_LIST -> parseSupplMeshList(reader, fields);
          case PubmedXmlElements.Keyword.KEYWORD_LIST -> parseKeywordList(reader, fields);
          case PubmedXmlElements.OtherAbstract.OTHER_ABSTRACT -> parseOtherAbstract(reader, fields);
          case PubmedXmlElements.Container.CITATION_SUBSET ->
              fields.citationSubset = reader.getElementText().trim();
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Container.MEDLINE_CITATION.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 Article 元素。
  private void parseArticle(XMLStreamReader reader, ParsedFields fields) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Journal.JOURNAL -> parseJournal(reader, fields);
          case PubmedXmlElements.Article.ARTICLE_TITLE ->
              fields.articleTitle = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Article.VERNACULAR_TITLE ->
              fields.vernacularTitle =
                  XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Article.LANGUAGE ->
              fields.languages.add(XmlParsingHelper.getElementTextWithMixedContent(reader).trim());
          case PubmedXmlElements.Article.AUTHOR_LIST -> parseAuthorList(reader, fields);
          case PubmedXmlElements.Article.ABSTRACT -> parseAbstract(reader, fields);
          case PubmedXmlElements.Article.PUBLICATION_TYPE_LIST ->
              parsePublicationTypeList(reader, fields);
          case PubmedXmlElements.Article.PAGINATION -> parsePagination(reader, fields);
          case PubmedXmlElements.Article.E_LOCATION_ID -> parseELocationId(reader, fields);
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Container.ARTICLE.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 Journal 元素。
  private void parseJournal(XMLStreamReader reader, ParsedFields fields) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Journal.ISSN -> parseIssn(reader, fields);
          case PubmedXmlElements.Journal.JOURNAL_ISSUE -> parseJournalIssue(reader, fields);
          case PubmedXmlElements.Journal.TITLE ->
              fields.journalTitle = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Journal.ISO_ABBREVIATION ->
              fields.isoAbbreviation =
                  XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Journal.JOURNAL.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 ISSN 元素（根据 IssnType 属性区分）。
  private void parseIssn(XMLStreamReader reader, ParsedFields fields) throws XMLStreamException {
    String issnType = reader.getAttributeValue(null, PubmedXmlElements.Attribute.ISSN_TYPE);
    String issn = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();

    if (PubmedXmlElements.Attribute.ISSN_TYPE_PRINT.equals(issnType)) {
      fields.issnPrint = issn;
    } else if (PubmedXmlElements.Attribute.ISSN_TYPE_ELECTRONIC.equals(issnType)) {
      fields.issnElectronic = issn;
    }
  }

  /// 解析 JournalIssue 元素。
  private void parseJournalIssue(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Journal.VOLUME ->
              fields.volume = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Journal.ISSUE ->
              fields.issue = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Date.PUB_DATE -> parsePubDate(reader, fields);
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Journal.JOURNAL_ISSUE.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 PubDate 元素。
  private void parsePubDate(XMLStreamReader reader, ParsedFields fields) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Date.YEAR -> {
            String yearStr = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
            fields.pubYear = parseIntSafe(yearStr);
          }
          case PubmedXmlElements.Date.MONTH ->
              fields.pubMonthRaw = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Date.SEASON ->
              fields.pubSeason = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Date.DAY -> {
            String dayStr = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
            fields.pubDay = parseIntSafe(dayStr);
          }
          case PubmedXmlElements.Date.MEDLINE_DATE -> {
            fields.medlineDate = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
            // 从 MedlineDate 提取年份
            if (fields.pubYear == null) {
              fields.pubYear = extractYearFromMedlineDate(fields.medlineDate);
            }
          }
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Date.PUB_DATE.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 MedlineJournalInfo 元素。
  private void parseMedlineJournalInfo(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Identifier.NLM_UNIQUE_ID ->
              fields.nlmUniqueId = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Journal.ISSN_LINKING ->
              fields.issnLinking = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Journal.MEDLINE_TA ->
              fields.medlineTa = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Journal.MEDLINE_JOURNAL_INFO.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 PubmedData 元素。
  private void parsePubmedData(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Article.PUBLICATION_STATUS ->
              fields.publicationStatus =
                  XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Identifier.ARTICLE_ID_LIST -> parseArticleIdList(reader, fields);
          case PubmedXmlElements.Date.HISTORY -> parseHistory(reader, fields);
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Container.PUBMED_DATA.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 History 元素，只取 `PubStatus="pmc-release"` 的 PMC 放开日期。
  ///
  /// 其余 `PubStatus` 的日期不在本解析器的范围内，直接跳过。
  private void parseHistory(XMLStreamReader reader, ParsedFields fields) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        String pubStatus = reader.getAttributeValue(null, PubmedXmlElements.Attribute.PUB_STATUS);
        if (PubmedXmlElements.Date.PUBMED_PUB_DATE.equals(localName)
            && PubmedXmlElements.Attribute.PUB_STATUS_PMC_RELEASE.equals(pubStatus)) {
          fields.pmcRelease = parsePubMedPubDate(reader);
        } else {
          XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Date.HISTORY.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析一个 PubMedPubDate 元素为按精度记录的日期，缺的月 / 日不补；年份缺失时返回 `null`。
  private PartialDate parsePubMedPubDate(XMLStreamReader reader) throws XMLStreamException {
    Integer year = null;
    String month = null;
    Integer day = null;
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Date.YEAR ->
              year = parseIntSafe(XmlParsingHelper.getElementTextWithMixedContent(reader).trim());
          case PubmedXmlElements.Date.MONTH ->
              month = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Date.DAY ->
              day = parseIntSafe(XmlParsingHelper.getElementTextWithMixedContent(reader).trim());
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Date.PUBMED_PUB_DATE.equals(reader.getLocalName())) {
        break;
      }
    }
    return toPartialDate(year, month, day, null, null);
  }

  /// 解析 ArticleIdList 元素。
  private void parseArticleIdList(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Identifier.ARTICLE_ID.equals(reader.getLocalName())) {
        String idType = reader.getAttributeValue(null, PubmedXmlElements.Attribute.ID_TYPE);
        String value = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();

        if (PubmedXmlElements.Attribute.ID_TYPE_DOI.equals(idType)) {
          fields.doi = value;
        } else if (PubmedXmlElements.Attribute.ID_TYPE_PMC.equals(idType)) {
          fields.pmcId = value;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Identifier.ARTICLE_ID_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  // ========== 新增解析方法 ==========

  /// 解析 AuthorList 元素。
  private void parseAuthorList(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    // 先提取 CompleteYN 属性
    fields.authorsComplete =
        XmlParsingHelper.parseYesNoAttributeNullable(
            reader, PubmedXmlElements.Attribute.COMPLETE_YN);

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Author.AUTHOR.equals(reader.getLocalName())) {
        ParsedAuthor author = parseAuthor(reader);
        if (author != null) {
          fields.authors.add(author);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Article.AUTHOR_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析单个 Author 元素。
  private ParsedAuthor parseAuthor(XMLStreamReader reader) throws XMLStreamException {
    ParsedAuthor author = new ParsedAuthor();

    // 提取属性
    author.valid =
        XmlParsingHelper.parseYesNoAttributeNullable(reader, PubmedXmlElements.Attribute.VALID_YN);
    String equalContrib = reader.getAttributeValue(null, PubmedXmlElements.Attribute.EQUAL_CONTRIB);
    if ("Y".equals(equalContrib)) {
      author.equalContribution = true;
    }

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Author.LAST_NAME ->
              author.lastName = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Author.FORE_NAME ->
              author.foreName = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Author.INITIALS ->
              author.initials = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Author.SUFFIX ->
              author.suffix = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Author.COLLECTIVE_NAME ->
              author.collectiveName =
                  XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Author.AFFILIATION_INFO -> parseAffiliationInfo(reader, author);
          case PubmedXmlElements.Author.IDENTIFIER -> parseAuthorIdentifier(reader, author);
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Author.AUTHOR.equals(reader.getLocalName())) {
        break;
      }
    }

    return author;
  }

  /// 解析 AffiliationInfo 元素。
  private void parseAffiliationInfo(XMLStreamReader reader, ParsedAuthor author)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Author.AFFILIATION.equals(reader.getLocalName())) {
        String affiliation = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
        if (!affiliation.isBlank()) {
          author.affiliations.add(affiliation);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Author.AFFILIATION_INFO.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析作者 Identifier 元素（主要提取 ORCID）。
  private void parseAuthorIdentifier(XMLStreamReader reader, ParsedAuthor author)
      throws XMLStreamException {
    String source = reader.getAttributeValue(null, PubmedXmlElements.Attribute.SOURCE);
    String value = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();

    if (PubmedXmlElements.Attribute.SOURCE_ORCID.equals(source) && !value.isBlank()) {
      author.orcid = value;
    }
  }

  /// 解析 Abstract 元素。
  ///
  /// XML 结构：
  /// ```xml
  /// <Abstract>
  ///   <AbstractText Label="BACKGROUND">背景</AbstractText>
  ///   <CopyrightInformation>版权信息</CopyrightInformation>
  /// </Abstract>
  /// ```
  private void parseAbstract(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Article.ABSTRACT_TEXT -> {
            ParsedAbstractSection section = new ParsedAbstractSection();
            section.label = reader.getAttributeValue(null, PubmedXmlElements.Attribute.LABEL);
            section.content = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
            // 粘连标签拆分按原文判断：空白段落虽然丢弃，但它的段数与 Label 仍要算上
            fields.rawAbstractTextCount++;
            fields.rawAbstractHasLabel |= section.label != null;
            if (!section.content.isBlank()) {
              fields.abstractSections.add(section);
            }
          }
          case PubmedXmlElements.Article.COPYRIGHT_INFORMATION ->
              fields.abstractCopyright =
                  XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Article.ABSTRACT.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 MeshHeadingList 元素。
  private void parseMeshHeadingList(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.MeSH.MESH_HEADING.equals(reader.getLocalName())) {
        ParsedMeshHeading heading = parseMeshHeading(reader);
        if (heading != null && heading.descriptorName != null) {
          fields.meshHeadings.add(heading);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.MeSH.MESH_HEADING_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析单个 MeshHeading 元素。
  private ParsedMeshHeading parseMeshHeading(XMLStreamReader reader) throws XMLStreamException {
    ParsedMeshHeading heading = new ParsedMeshHeading();

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (PubmedXmlElements.MeSH.DESCRIPTOR_NAME.equals(localName)) {
          heading.descriptorUi = reader.getAttributeValue(null, PubmedXmlElements.Attribute.UI);
          heading.descriptorMajorTopic =
              XmlParsingHelper.parseYesNoAttributeNullable(
                  reader, PubmedXmlElements.Attribute.MAJOR_TOPIC_YN);
          heading.descriptorName = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
        } else if (PubmedXmlElements.MeSH.QUALIFIER_NAME.equals(localName)) {
          ParsedQualifier qualifier = new ParsedQualifier();
          qualifier.ui = reader.getAttributeValue(null, PubmedXmlElements.Attribute.UI);
          qualifier.majorTopic =
              XmlParsingHelper.parseYesNoAttributeNullable(
                  reader, PubmedXmlElements.Attribute.MAJOR_TOPIC_YN);
          qualifier.name = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          heading.qualifiers.add(qualifier);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.MeSH.MESH_HEADING.equals(reader.getLocalName())) {
        break;
      }
    }

    return heading;
  }

  /// 解析 SupplMeshList 元素。
  private void parseSupplMeshList(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.SupplMesh.SUPPL_MESH_NAME.equals(reader.getLocalName())) {
        ParsedSupplMeshName supplMesh = parseSupplMeshName(reader);
        if (supplMesh != null && supplMesh.ui != null) {
          fields.supplMeshNames.add(supplMesh);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.SupplMesh.SUPPL_MESH_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析单个 SupplMeshName 元素。
  ///
  /// XML 结构：`<SupplMeshName UI="C538003" Type="Disease">Aspirin-sensitive asthma</SupplMeshName>`
  private ParsedSupplMeshName parseSupplMeshName(XMLStreamReader reader) throws XMLStreamException {
    ParsedSupplMeshName supplMesh = new ParsedSupplMeshName();
    supplMesh.ui = reader.getAttributeValue(null, PubmedXmlElements.Attribute.UI);
    supplMesh.type = reader.getAttributeValue(null, PubmedXmlElements.Attribute.TYPE);
    supplMesh.name = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
    return supplMesh;
  }

  /// 解析 KeywordList 元素。
  ///
  /// XML 结构：
  /// ```xml
  /// <KeywordList Owner="NOTNLM">
  ///   <Keyword MajorTopicYN="N">Machine learning</Keyword>
  ///   <Keyword MajorTopicYN="Y">Drug discovery</Keyword>
  /// </KeywordList>
  /// ```
  private void parseKeywordList(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    var keywordSet = new ParsedKeywordSet();
    keywordSet.owner = reader.getAttributeValue(null, PubmedXmlElements.Attribute.OWNER);

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Keyword.KEYWORD.equals(reader.getLocalName())) {
        ParsedKeyword keyword = parseKeyword(reader);
        if (keyword != null && keyword.term != null && !keyword.term.isBlank()) {
          keywordSet.keywords.add(keyword);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Keyword.KEYWORD_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    if (!keywordSet.keywords.isEmpty()) {
      fields.keywordSets.add(keywordSet);
    }
  }

  /// 解析单个 Keyword 元素。
  ///
  /// XML 结构：`<Keyword MajorTopicYN="N">Machine learning</Keyword>`
  private ParsedKeyword parseKeyword(XMLStreamReader reader) throws XMLStreamException {
    var keyword = new ParsedKeyword();
    keyword.majorTopic =
        XmlParsingHelper.parseYesNoAttributeNullable(
            reader, PubmedXmlElements.Attribute.MAJOR_TOPIC_YN);
    keyword.term = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
    return keyword;
  }

  /// 解析 PublicationTypeList 元素。
  private void parsePublicationTypeList(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Article.PUBLICATION_TYPE.equals(reader.getLocalName())) {
        ParsedPublicationType pubType = new ParsedPublicationType();
        pubType.ui = reader.getAttributeValue(null, PubmedXmlElements.Attribute.UI);
        pubType.value = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
        if (!pubType.value.isBlank()) {
          fields.publicationTypes.add(pubType);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Article.PUBLICATION_TYPE_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 OtherAbstract 元素。
  ///
  /// XML 结构：
  /// ```xml
  /// <OtherAbstract Language="chi" Type="Publisher">
  ///   <AbstractText>摘要文本</AbstractText>
  ///   <AbstractText Label="BACKGROUND">背景</AbstractText>
  ///   <CopyrightInformation>版权信息</CopyrightInformation>
  /// </OtherAbstract>
  /// ```
  private void parseOtherAbstract(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    ParsedOtherAbstract otherAbstract = new ParsedOtherAbstract();

    // 提取 Language 和 Type 属性
    String language = reader.getAttributeValue(null, PubmedXmlElements.Attribute.LANGUAGE);
    otherAbstract.language = normalizeLanguageCode(language);
    otherAbstract.type = reader.getAttributeValue(null, PubmedXmlElements.Attribute.TYPE);

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Article.ABSTRACT_TEXT -> {
            ParsedAbstractSection section = new ParsedAbstractSection();
            section.label = reader.getAttributeValue(null, PubmedXmlElements.Attribute.LABEL);
            section.content = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
            if (!section.content.isBlank()) {
              otherAbstract.sections.add(section);
            }
          }
          case PubmedXmlElements.OtherAbstract.COPYRIGHT_INFORMATION ->
              otherAbstract.copyright =
                  XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.OtherAbstract.OTHER_ABSTRACT.equals(reader.getLocalName())) {
        break;
      }
    }

    // 只添加有内容的摘要
    if (!otherAbstract.sections.isEmpty()) {
      fields.otherAbstracts.add(otherAbstract);
    }
  }

  /// 解析 Pagination 元素：`StartPage`、`EndPage`、`MedlinePgn` 都可能出现。
  private void parsePagination(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Article.MEDLINE_PGN ->
              fields.medlinePgn = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Article.START_PAGE ->
              fields.startPage = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          case PubmedXmlElements.Article.END_PAGE ->
              fields.endPage = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Article.PAGINATION.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 ELocationID 元素：`ValidYN="N"` 的不收；pii 取第一个，doi 只作 `ArticleIdList` 没有 DOI 时的后备。
  private void parseELocationId(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    String type = reader.getAttributeValue(null, PubmedXmlElements.Attribute.E_ID_TYPE);
    String valid = reader.getAttributeValue(null, PubmedXmlElements.Attribute.VALID_YN);
    String value = XmlParsingHelper.getElementTextWithMixedContent(reader).trim();
    if ("N".equals(valid) || value.isEmpty()) {
      return;
    }
    if ("pii".equals(type) && fields.elocationPii == null) {
      fields.elocationPii = value;
    } else if ("doi".equals(type) && fields.elocationDoi == null) {
      fields.elocationDoi = value;
    }
  }

  // ========== 辅助方法 ==========

  /// 解析月份（支持数字和英文缩写），认不出或超出范围时返回 `null`。
  private Integer parseMonth(String monthStr) {
    if (monthStr == null || monthStr.isBlank()) {
      return null;
    }

    // 尝试解析数字（超出 1 ~ 12 视为认不出）
    try {
      int month = Integer.parseInt(monthStr);
      return month >= 1 && month <= 12 ? month : null;
    } catch (NumberFormatException e) {
      // 尝试解析英文缩写
      return MONTH_MAP.get(monthStr.toLowerCase(Locale.ROOT));
    }
  }

  /// 由日期部件组装按精度记录的日期，不补缺失的月 / 日。
  ///
  /// - 年份为空：返回 `null`（整篇记录会被当作无效跳过）
  /// - 月份认不出（区间 `Jun-Jul`、超出 1 ~ 12）：丢掉月份，原文放进 `text`
  /// - 日对该月不合法（2 月 30 日）或没有月份：丢掉日
  ///
  /// @param year 年
  /// @param monthRaw 月份原文，可空
  /// @param day 日，可空
  /// @param season 季节原文，可空
  /// @param text 其他原文（如 `MedlineDate`），可空
  /// @return 按精度记录的日期，年份为空时为 `null`
  private PartialDate toPartialDate(
      Integer year, String monthRaw, Integer day, String season, String text) {
    if (year == null) {
      return null;
    }
    Integer month = parseMonth(monthRaw);
    String rawText = text;
    if (month == null && monthRaw != null && !monthRaw.isBlank() && rawText == null) {
      rawText = monthRaw;
    }
    Integer validDay =
        month != null && day != null && YearMonth.of(year, month).isValidDay(day) ? day : null;
    String seasonText = season == null || season.isBlank() ? null : season;
    return new PartialDate(year, month, validDay, seasonText, rawText);
  }

  /// 从 MedlineDate 提取年份。
  private Integer extractYearFromMedlineDate(String medlineDate) {
    if (medlineDate == null) {
      return null;
    }
    Matcher matcher = YEAR_PATTERN.matcher(medlineDate);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1));
    }
    return null;
  }

  /// 是否为空或空白。
  ///
  /// @param value 字符串，可空
  /// @return 为 null 或只含空白时为 `true`
  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /// 安全解析整数。
  private Integer parseIntSafe(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /// 标准化语言代码（小写 + 去空格）。
  ///
  /// @param rawLang 原始语言代码（如 "eng", "chi"）
  /// @return 标准化后的 ISO 639-3 代码，空值返回 null
  private String normalizeLanguageCode(String rawLang) {
    if (rawLang == null || rawLang.isBlank()) {
      return null;
    }
    return rawLang.trim().toLowerCase(Locale.ROOT);
  }

  /// 构建 CanonicalPublication。
  private CanonicalPublication buildPublication(ParsedFields fields) {
    return CanonicalPublication.builder()
        .identifiers(buildIdentifiers(fields))
        .title(fields.articleTitle)
        .originalTitle(fields.vernacularTitle)
        .language(buildLanguage(fields))
        .publicationStatus(fields.publicationStatus)
        .authorsComplete(fields.authorsComplete)
        .authors(buildAuthors(fields))
        .abstractContent(buildAbstract(fields))
        .alternativeAbstracts(buildAlternativeAbstracts(fields))
        .meshHeadings(buildMeshHeadings(fields))
        .supplMeshNames(buildSupplMeshNames(fields))
        .keywords(buildKeywordSets(fields))
        .publicationTypes(buildPublicationTypes(fields))
        .pagination(buildPagination(fields))
        .journal(buildJournal(fields))
        .dates(buildDates(fields))
        .metadata(buildMetadata(fields))
        .build();
  }

  /// 构建出版物元数据（MedlineCitation 属性）。
  private CanonicalPublication.PublicationMetadata buildMetadata(ParsedFields fields) {
    return CanonicalPublication.PublicationMetadata.builder()
        .status(fields.citationStatus)
        .owner(fields.citationOwner)
        .indexingMethod(fields.indexingMethod)
        .citationSubset(fields.citationSubset)
        .build();
  }

  /// 构建标识符列表。
  private List<Identifier> buildIdentifiers(ParsedFields fields) {
    List<Identifier> identifiers = new ArrayList<>(3);

    // PMID（必填）
    if (fields.pmid != null) {
      identifiers.add(
          Identifier.builder().type(PublicationIdentifierType.PMID).value(fields.pmid).build());
    }

    // DOI（可选）：ArticleIdList 优先，没有时用 ELocationID 的 doi
    String doi = fields.doi != null && !fields.doi.isBlank() ? fields.doi : fields.elocationDoi;
    if (doi != null && !doi.isBlank()) {
      identifiers.add(Identifier.builder().type(PublicationIdentifierType.DOI).value(doi).build());
    }

    // PMC（可选）
    if (fields.pmcId != null && !fields.pmcId.isBlank()) {
      identifiers.add(
          Identifier.builder().type(PublicationIdentifierType.PMC).value(fields.pmcId).build());
    }

    // PII（可选）：来自 ELocationID，文章号或出版商条目号
    if (fields.elocationPii != null) {
      identifiers.add(
          Identifier.builder()
              .type(PublicationIdentifierType.PII)
              .value(fields.elocationPii)
              .build());
    }

    return identifiers;
  }

  /// 构建语言代码（取第一个语言，保留原始 ISO 639-3 代码）。
  private String buildLanguage(ParsedFields fields) {
    if (fields.languages.isEmpty()) {
      return null;
    }
    return normalizeLanguageCode(fields.languages.getFirst());
  }

  /// 构建期刊信息。
  ///
  /// ISSN 存储策略：优先存储 Print ISSN，issnLinking 单独存储。
  private Journal buildJournal(ParsedFields fields) {
    // 确定主 ISSN 和类型（优先 Print）
    String issn;
    String issnType;
    if (fields.issnPrint != null && !fields.issnPrint.isBlank()) {
      issn = fields.issnPrint;
      issnType = "print";
    } else if (fields.issnElectronic != null && !fields.issnElectronic.isBlank()) {
      issn = fields.issnElectronic;
      issnType = "electronic";
    } else {
      issn = null;
      issnType = null;
    }

    return Journal.builder()
        .title(fields.journalTitle)
        .nlmUniqueId(fields.nlmUniqueId)
        .issn(issn)
        .issnType(issnType)
        .issnLinking(fields.issnLinking)
        .isoAbbreviation(fields.isoAbbreviation)
        .medlineAbbreviation(fields.medlineTa)
        .volume(fields.volume)
        .issue(fields.issue)
        .build();
  }

  /// 构建出版日期，按实际精度记录（见 [#toPartialDate]）。
  private PublicationDates buildDates(ParsedFields fields) {
    return PublicationDates.builder()
        .published(
            toPartialDate(
                fields.pubYear,
                fields.pubMonthRaw,
                fields.pubDay,
                fields.pubSeason,
                fields.medlineDate))
        .pmcRelease(fields.pmcRelease)
        .build();
  }

  /// 构建作者列表。
  private List<Author> buildAuthors(ParsedFields fields) {
    if (fields.authors.isEmpty()) {
      return List.of();
    }

    List<Author> authors = new ArrayList<>(fields.authors.size());
    for (ParsedAuthor parsed : fields.authors) {
      Author author = buildAuthor(parsed);
      if (author != null) {
        authors.add(author);
      }
    }
    return authors;
  }

  /// 构建单个作者。
  private Author buildAuthor(ParsedAuthor parsed) {
    // 必须有姓名或组织名称
    if (parsed.lastName == null && parsed.collectiveName == null) {
      return null;
    }

    List<Affiliation> affiliations =
        parsed.affiliations.stream().map(text -> Affiliation.builder().name(text).build()).toList();

    // 构建 ORCID 标识符
    List<Identifier> identifiers =
        parsed.orcid != null
            ? List.of(
                Identifier.builder()
                    .type(PublicationIdentifierType.ORCID)
                    .value(parsed.orcid)
                    .build())
            : List.of();

    return Author.builder()
        .lastName(parsed.lastName)
        .foreName(parsed.foreName)
        .initials(parsed.initials)
        .suffix(parsed.suffix)
        .organizationName(parsed.collectiveName)
        .equalContribution(parsed.equalContribution)
        .valid(parsed.valid)
        .affiliations(affiliations)
        .identifiers(identifiers)
        .build();
  }

  /// 构建摘要。
  ///
  /// 支持结构化摘要（带 Label）和非结构化摘要。
  private Abstract buildAbstract(ParsedFields fields) {
    if (fields.abstractSections.isEmpty()) {
      return null;
    }

    List<AbstractSection> sections =
        splitRunInLabels(fields).stream()
            .map(
                parsed ->
                    AbstractSection.builder().label(parsed.label).content(parsed.content).build())
            .toList();

    return Abstract.builder()
        .sections(sections)
        .copyright(blankToNull(fields.abstractCopyright))
        .build();
  }

  /// 拆开粘在正文前的段落标签。
  ///
  /// 只处理原文"整篇只有一段 `AbstractText`、且没有 Label"的摘要（见 [RunInAbstractLabelSplitter]）；
  /// 原文有带 Label 的段落时，即使它因内容为空被丢弃，也整篇不动；
  /// 其余情况、或拆分条件不满足时原样返回。
  ///
  /// @param fields 解析出的字段（摘要段落与原文段数、Label 状态）
  /// @return 拆分后的段落
  private List<ParsedAbstractSection> splitRunInLabels(ParsedFields fields) {
    List<ParsedAbstractSection> parsed = fields.abstractSections;
    if (fields.rawAbstractTextCount != 1 || fields.rawAbstractHasLabel || parsed.size() != 1) {
      return parsed;
    }
    List<LabeledText> split = RunInAbstractLabelSplitter.split(parsed.getFirst().content);
    if (split.isEmpty()) {
      return parsed;
    }
    return split.stream()
        .map(
            labeled -> {
              ParsedAbstractSection section = new ParsedAbstractSection();
              section.label = labeled.label();
              section.content = labeled.content();
              return section;
            })
        .toList();
  }

  /// 构建 MeSH 主题词列表。
  private List<MeshHeading> buildMeshHeadings(ParsedFields fields) {
    if (fields.meshHeadings.isEmpty()) {
      return List.of();
    }

    return fields.meshHeadings.stream().map(this::buildMeshHeading).toList();
  }

  /// 构建单个 MeSH 主题词。
  private MeshHeading buildMeshHeading(ParsedMeshHeading parsed) {
    DescriptorName descriptor =
        DescriptorName.builder()
            .ui(parsed.descriptorUi)
            .term(parsed.descriptorName)
            .majorTopic(Boolean.TRUE.equals(parsed.descriptorMajorTopic))
            .build();

    List<QualifierName> qualifiers =
        parsed.qualifiers.stream()
            .map(
                q ->
                    QualifierName.builder()
                        .ui(q.ui)
                        .term(q.name)
                        .majorTopic(Boolean.TRUE.equals(q.majorTopic))
                        .build())
            .toList();

    return MeshHeading.builder().descriptorName(descriptor).qualifierNames(qualifiers).build();
  }

  /// 构建发表类型列表。
  private List<PublicationType> buildPublicationTypes(ParsedFields fields) {
    if (fields.publicationTypes.isEmpty()) {
      return List.of();
    }

    return fields.publicationTypes.stream()
        .map(parsed -> PublicationType.builder().id(parsed.ui).value(parsed.value).build())
        .toList();
  }

  /// 构建页码信息。
  private Pagination buildPagination(ParsedFields fields) {
    if (isBlank(fields.medlinePgn) && isBlank(fields.startPage) && isBlank(fields.endPage)) {
      return null;
    }

    return Pagination.builder()
        .medlinePgn(blankToNull(fields.medlinePgn))
        .startPage(blankToNull(fields.startPage))
        .endPage(blankToNull(fields.endPage))
        .build();
  }

  /// 构建补充 MeSH 概念列表。
  private List<SupplMeshName> buildSupplMeshNames(ParsedFields fields) {
    if (fields.supplMeshNames.isEmpty()) {
      return List.of();
    }

    return fields.supplMeshNames.stream()
        .map(
            parsed ->
                SupplMeshName.builder().ui(parsed.ui).name(parsed.name).type(parsed.type).build())
        .toList();
  }

  /// 构建关键词集合列表。
  private List<KeywordSet> buildKeywordSets(ParsedFields fields) {
    if (fields.keywordSets.isEmpty()) {
      return List.of();
    }

    return fields.keywordSets.stream().map(this::buildKeywordSet).toList();
  }

  /// 构建单个关键词集合。
  private KeywordSet buildKeywordSet(ParsedKeywordSet parsed) {
    List<Keyword> keywords =
        parsed.keywords.stream()
            .map(
                k ->
                    Keyword.builder()
                        .majorTopic(Boolean.TRUE.equals(k.majorTopic))
                        .term(k.term)
                        .build())
            .toList();

    return KeywordSet.builder().source(parsed.owner).keywords(keywords).build();
  }

  /// 构建其他语言摘要列表。
  ///
  /// 将解析的 OtherAbstract 转换为 AlternativeAbstract 列表。
  /// 结构化摘要的段落会拼接成完整文本。
  private List<CanonicalPublication.AlternativeAbstract> buildAlternativeAbstracts(
      ParsedFields fields) {
    if (fields.otherAbstracts.isEmpty()) {
      return List.of();
    }

    return fields.otherAbstracts.stream().map(this::buildAlternativeAbstract).toList();
  }

  /// 构建单个其他语言摘要。
  ///
  /// 同时保留结构化段落（含 label）与拼接后的完整文本。
  private CanonicalPublication.AlternativeAbstract buildAlternativeAbstract(
      ParsedOtherAbstract parsed) {
    // 拼接所有段落为完整文本
    String text = joinAbstractSections(parsed.sections);

    List<AbstractSection> sections =
        parsed.sections.stream()
            .map(
                section ->
                    AbstractSection.builder().label(section.label).content(section.content).build())
            .toList();

    return CanonicalPublication.AlternativeAbstract.builder()
        .language(parsed.language)
        .type(parsed.type)
        .text(text)
        .sections(sections)
        .copyright(blankToNull(parsed.copyright))
        .build();
  }

  /// 拼接摘要段落为完整文本。
  ///
  /// 对于结构化摘要，按顺序拼接各段落内容。
  private String joinAbstractSections(List<ParsedAbstractSection> sections) {
    if (sections == null || sections.isEmpty()) {
      return null;
    }

    if (sections.size() == 1) {
      return sections.getFirst().content;
    }

    // 多段落拼接，用空格分隔
    return sections.stream()
        .map(s -> s.content)
        .filter(content -> content != null && !content.isBlank())
        .reduce((a, b) -> a + " " + b)
        .orElse(null);
  }

  /// 将空白字符串归一为 `null`。
  ///
  /// PubMed XML 中 `CopyrightInformation` 可能为空元素或仅含空白，
  /// 此时应视为「无版权信息」而非空串。
  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  // ========== 内部类 ==========

  /// 解析字段容器。
  private static class ParsedFields {
    // 标识符
    String pmid;
    String doi;
    String pmcId;

    // 标题
    String articleTitle;
    String vernacularTitle;

    // 期刊信息
    String nlmUniqueId;
    String issnPrint;
    String issnElectronic;
    String issnLinking;
    String journalTitle;
    String volume;
    String issue;

    // 日期
    Integer pubYear;
    String pubMonthRaw;
    Integer pubDay;
    String pubSeason;
    String medlineDate;
    PartialDate pmcRelease;

    // 元数据
    List<String> languages = new ArrayList<>();
    String publicationStatus;
    Boolean authorsComplete;
    String citationStatus;
    String citationOwner;
    String indexingMethod;
    String citationSubset;

    // 作者信息
    List<ParsedAuthor> authors = new ArrayList<>();

    // 摘要
    List<ParsedAbstractSection> abstractSections = new ArrayList<>();
    String abstractCopyright;
    /// 原文 `AbstractText` 段数（含内容为空被丢弃的）
    int rawAbstractTextCount;
    /// 原文是否有任何一段带 Label（含内容为空被丢弃的）
    boolean rawAbstractHasLabel;

    // MeSH 主题词
    List<ParsedMeshHeading> meshHeadings = new ArrayList<>();

    // 补充 MeSH 概念
    List<ParsedSupplMeshName> supplMeshNames = new ArrayList<>();

    // 关键词
    List<ParsedKeywordSet> keywordSets = new ArrayList<>();

    // 发表类型
    List<ParsedPublicationType> publicationTypes = new ArrayList<>();

    // 页码
    String medlinePgn;
    String startPage;
    String endPage;
    String elocationPii;
    String elocationDoi;
    String isoAbbreviation;
    String medlineTa;

    // 其他语言摘要
    List<ParsedOtherAbstract> otherAbstracts = new ArrayList<>();
  }

  /// 解析的作者信息。
  private static class ParsedAuthor {
    String lastName;
    String foreName;
    String initials;
    String suffix;
    String collectiveName;
    Boolean valid;
    Boolean equalContribution;
    List<String> affiliations = new ArrayList<>();
    String orcid;
  }

  /// 解析的摘要段落。
  private static class ParsedAbstractSection {
    String label;
    String content;
  }

  /// 解析的 MeSH 主题词。
  private static class ParsedMeshHeading {
    String descriptorUi;
    String descriptorName;
    Boolean descriptorMajorTopic;
    List<ParsedQualifier> qualifiers = new ArrayList<>();
  }

  /// 解析的 MeSH 限定词。
  private static class ParsedQualifier {
    String ui;
    String name;
    Boolean majorTopic;
  }

  /// 解析的发表类型。
  private static class ParsedPublicationType {
    String ui;
    String value;
  }

  /// 解析的补充 MeSH 概念。
  private static class ParsedSupplMeshName {
    String ui;
    String name;
    String type;
  }

  /// 解析的关键词集合。
  private static class ParsedKeywordSet {
    String owner;
    List<ParsedKeyword> keywords = new ArrayList<>();
  }

  /// 解析的单个关键词。
  private static class ParsedKeyword {
    Boolean majorTopic;
    String term;
  }

  /// 解析的其他语言摘要。
  private static class ParsedOtherAbstract {
    String language;
    String type;
    String copyright;
    List<ParsedAbstractSection> sections = new ArrayList<>();
  }
}
