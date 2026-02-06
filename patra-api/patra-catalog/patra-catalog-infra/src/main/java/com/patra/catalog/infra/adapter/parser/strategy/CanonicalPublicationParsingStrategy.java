package com.patra.catalog.infra.adapter.parser.strategy;

import com.patra.catalog.infra.adapter.parser.PubmedXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingHelper;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.CanonicalPublication.Abstract;
import com.patra.common.model.CanonicalPublication.AbstractSection;
import com.patra.common.model.CanonicalPublication.Affiliation;
import com.patra.common.model.CanonicalPublication.Author;
import com.patra.common.model.CanonicalPublication.DescriptorName;
import com.patra.common.model.CanonicalPublication.Identifier;
import com.patra.common.model.CanonicalPublication.Journal;
import com.patra.common.model.CanonicalPublication.Keyword;
import com.patra.common.model.CanonicalPublication.KeywordSet;
import com.patra.common.model.CanonicalPublication.MeshHeading;
import com.patra.common.model.CanonicalPublication.Pagination;
import com.patra.common.model.CanonicalPublication.PublicationDates;
import com.patra.common.model.CanonicalPublication.PublicationType;
import com.patra.common.model.CanonicalPublication.QualifierName;
import com.patra.common.model.CanonicalPublication.SupplMeshName;
import com.patra.common.model.enums.PublicationIdentifierType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
/// 1. 多语言处理：取第一个语言，转换为 ISO 639-1 代码
/// 2. ISSN 存储：优先存储 Print ISSN，issnLinking 单独存储
/// 3. 日期转换：Year/Month/Day → LocalDate，缺失月/日用默认值 1
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

  /// PubMed 3 字母语言代码到 ISO 639-1 的映射。
  private static final Map<String, String> LANGUAGE_CODE_MAP =
      Map.ofEntries(
          Map.entry("eng", "en"),
          Map.entry("chi", "zh"),
          Map.entry("jpn", "ja"),
          Map.entry("kor", "ko"),
          Map.entry("ger", "de"),
          Map.entry("fre", "fr"),
          Map.entry("spa", "es"),
          Map.entry("ita", "it"),
          Map.entry("por", "pt"),
          Map.entry("rus", "ru"),
          Map.entry("ara", "ar"),
          Map.entry("dut", "nl"),
          Map.entry("pol", "pl"),
          Map.entry("tur", "tr"),
          Map.entry("hun", "hu"),
          Map.entry("cze", "cs"),
          Map.entry("dan", "da"),
          Map.entry("fin", "fi"),
          Map.entry("gre", "el"),
          Map.entry("heb", "he"),
          Map.entry("nor", "no"),
          Map.entry("swe", "sv"),
          Map.entry("ukr", "uk"));

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
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Identifier.PMID -> fields.pmid = reader.getElementText().trim();
          case PubmedXmlElements.Container.ARTICLE -> parseArticle(reader, fields);
          case PubmedXmlElements.Journal.MEDLINE_JOURNAL_INFO ->
              parseMedlineJournalInfo(reader, fields);
          case PubmedXmlElements.MeSH.MESH_HEADING_LIST -> parseMeshHeadingList(reader, fields);
          case PubmedXmlElements.SupplMesh.SUPPL_MESH_LIST -> parseSupplMeshList(reader, fields);
          case PubmedXmlElements.Keyword.KEYWORD_LIST -> parseKeywordList(reader, fields);
          case PubmedXmlElements.OtherAbstract.OTHER_ABSTRACT -> parseOtherAbstract(reader, fields);
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
              fields.articleTitle = reader.getElementText().trim();
          case PubmedXmlElements.Article.VERNACULAR_TITLE ->
              fields.vernacularTitle = reader.getElementText().trim();
          case PubmedXmlElements.Article.LANGUAGE ->
              fields.languages.add(reader.getElementText().trim());
          case PubmedXmlElements.Article.AUTHOR_LIST -> parseAuthorList(reader, fields);
          case PubmedXmlElements.Article.ABSTRACT -> parseAbstract(reader, fields);
          case PubmedXmlElements.Article.PUBLICATION_TYPE_LIST ->
              parsePublicationTypeList(reader, fields);
          case PubmedXmlElements.Article.PAGINATION -> parsePagination(reader, fields);
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
              fields.journalTitle = reader.getElementText().trim();
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
    String issn = reader.getElementText().trim();

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
          case PubmedXmlElements.Journal.VOLUME -> fields.volume = reader.getElementText().trim();
          case PubmedXmlElements.Journal.ISSUE -> fields.issue = reader.getElementText().trim();
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
            String yearStr = reader.getElementText().trim();
            fields.pubYear = parseIntSafe(yearStr);
          }
          case PubmedXmlElements.Date.MONTH -> {
            String monthStr = reader.getElementText().trim();
            fields.pubMonth = parseMonth(monthStr);
          }
          case PubmedXmlElements.Date.DAY -> {
            String dayStr = reader.getElementText().trim();
            fields.pubDay = parseIntSafe(dayStr);
          }
          case PubmedXmlElements.Date.MEDLINE_DATE -> {
            fields.medlineDate = reader.getElementText().trim();
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
              fields.nlmUniqueId = reader.getElementText().trim();
          case PubmedXmlElements.Journal.ISSN_LINKING ->
              fields.issnLinking = reader.getElementText().trim();
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
              fields.publicationStatus = reader.getElementText().trim();
          case PubmedXmlElements.Identifier.ARTICLE_ID_LIST -> parseArticleIdList(reader, fields);
          default -> XmlParsingHelper.skipElement(reader, localName);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Container.PUBMED_DATA.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析 ArticleIdList 元素。
  private void parseArticleIdList(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Identifier.ARTICLE_ID.equals(reader.getLocalName())) {
        String idType = reader.getAttributeValue(null, PubmedXmlElements.Attribute.ID_TYPE);
        String value = reader.getElementText().trim();

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
              author.lastName = reader.getElementText().trim();
          case PubmedXmlElements.Author.FORE_NAME ->
              author.foreName = reader.getElementText().trim();
          case PubmedXmlElements.Author.INITIALS ->
              author.initials = reader.getElementText().trim();
          case PubmedXmlElements.Author.SUFFIX -> author.suffix = reader.getElementText().trim();
          case PubmedXmlElements.Author.COLLECTIVE_NAME ->
              author.collectiveName = reader.getElementText().trim();
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
        String affiliation = reader.getElementText().trim();
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
    String value = reader.getElementText().trim();

    if (PubmedXmlElements.Attribute.SOURCE_ORCID.equals(source) && !value.isBlank()) {
      author.orcid = value;
    }
  }

  /// 解析 Abstract 元素。
  private void parseAbstract(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Article.ABSTRACT_TEXT.equals(reader.getLocalName())) {
        ParsedAbstractSection section = new ParsedAbstractSection();
        section.label = reader.getAttributeValue(null, PubmedXmlElements.Attribute.LABEL);
        section.content = reader.getElementText().trim();
        if (!section.content.isBlank()) {
          fields.abstractSections.add(section);
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
          heading.descriptorName = reader.getElementText().trim();
        } else if (PubmedXmlElements.MeSH.QUALIFIER_NAME.equals(localName)) {
          ParsedQualifier qualifier = new ParsedQualifier();
          qualifier.ui = reader.getAttributeValue(null, PubmedXmlElements.Attribute.UI);
          qualifier.majorTopic =
              XmlParsingHelper.parseYesNoAttributeNullable(
                  reader, PubmedXmlElements.Attribute.MAJOR_TOPIC_YN);
          qualifier.name = reader.getElementText().trim();
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
    supplMesh.name = reader.getElementText().trim();
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
    keyword.term = reader.getElementText().trim();
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
        pubType.value = reader.getElementText().trim();
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
    otherAbstract.language = convertLanguageCode(language);
    otherAbstract.type = reader.getAttributeValue(null, PubmedXmlElements.Attribute.TYPE);

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case PubmedXmlElements.Article.ABSTRACT_TEXT -> {
            ParsedAbstractSection section = new ParsedAbstractSection();
            section.label = reader.getAttributeValue(null, PubmedXmlElements.Attribute.LABEL);
            section.content = reader.getElementText().trim();
            if (!section.content.isBlank()) {
              otherAbstract.sections.add(section);
            }
          }
          case PubmedXmlElements.OtherAbstract.COPYRIGHT_INFORMATION ->
              otherAbstract.copyright = reader.getElementText().trim();
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

  /// 解析 Pagination 元素。
  private void parsePagination(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT
          && PubmedXmlElements.Article.MEDLINE_PGN.equals(reader.getLocalName())) {
        fields.medlinePgn = reader.getElementText().trim();
      } else if (event == XMLStreamConstants.END_ELEMENT
          && PubmedXmlElements.Article.PAGINATION.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  // ========== 辅助方法 ==========

  /// 解析月份（支持数字和英文缩写）。
  private Integer parseMonth(String monthStr) {
    if (monthStr == null || monthStr.isBlank()) {
      return null;
    }

    // 尝试解析数字
    try {
      return Integer.parseInt(monthStr);
    } catch (NumberFormatException e) {
      // 尝试解析英文缩写
      return MONTH_MAP.get(monthStr.toLowerCase());
    }
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

  /// 转换语言代码（PubMed 3 字母 → ISO 639-1）。
  ///
  /// @param pubmedLang PubMed 语言代码（如 "eng", "chi"）
  /// @return ISO 639-1 代码（如 "en", "zh"），未知语言返回 null
  private String convertLanguageCode(String pubmedLang) {
    if (pubmedLang == null || pubmedLang.isBlank()) {
      return null;
    }
    return LANGUAGE_CODE_MAP.get(pubmedLang.toLowerCase());
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

    // DOI（可选）
    if (fields.doi != null && !fields.doi.isBlank()) {
      identifiers.add(
          Identifier.builder().type(PublicationIdentifierType.DOI).value(fields.doi).build());
    }

    // PMC（可选）
    if (fields.pmcId != null && !fields.pmcId.isBlank()) {
      identifiers.add(
          Identifier.builder().type(PublicationIdentifierType.PMC).value(fields.pmcId).build());
    }

    return identifiers;
  }

  /// 构建语言代码（取第一个语言，转换为 ISO 639-1）。
  private String buildLanguage(ParsedFields fields) {
    if (fields.languages.isEmpty()) {
      return null;
    }
    return convertLanguageCode(fields.languages.getFirst());
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
        .volume(fields.volume)
        .issue(fields.issue)
        .build();
  }

  /// 构建出版日期。
  ///
  /// 日期转换规则：Year/Month/Day → LocalDate，缺失月/日用默认值 1。
  private PublicationDates buildDates(ParsedFields fields) {
    LocalDate publishedDate = null;

    if (fields.pubYear != null) {
      int month = fields.pubMonth != null ? fields.pubMonth : 1;
      int day = fields.pubDay != null ? fields.pubDay : 1;
      publishedDate = LocalDate.of(fields.pubYear, month, day);
    }

    return PublicationDates.builder().published(publishedDate).build();
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
        fields.abstractSections.stream()
            .map(
                parsed ->
                    AbstractSection.builder().label(parsed.label).content(parsed.content).build())
            .toList();

    return Abstract.builder().sections(sections).build();
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
    if (fields.medlinePgn == null || fields.medlinePgn.isBlank()) {
      return null;
    }

    return Pagination.builder().medlinePgn(fields.medlinePgn).build();
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
  private CanonicalPublication.AlternativeAbstract buildAlternativeAbstract(
      ParsedOtherAbstract parsed) {
    // 拼接所有段落为完整文本
    String text = joinAbstractSections(parsed.sections);

    return CanonicalPublication.AlternativeAbstract.builder()
        .language(parsed.language)
        .type(parsed.type)
        .text(text)
        .copyright(parsed.copyright)
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
    Integer pubMonth;
    Integer pubDay;
    String medlineDate;

    // 元数据
    List<String> languages = new ArrayList<>();
    String publicationStatus;
    Boolean authorsComplete;

    // 作者信息
    List<ParsedAuthor> authors = new ArrayList<>();

    // 摘要
    List<ParsedAbstractSection> abstractSections = new ArrayList<>();

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
