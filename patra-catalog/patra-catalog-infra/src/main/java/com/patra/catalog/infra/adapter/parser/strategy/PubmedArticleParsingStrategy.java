package com.patra.catalog.infra.adapter.parser.strategy;

import com.patra.catalog.domain.model.vo.publication.pubmed.PubmedArticle;
import com.patra.catalog.infra.adapter.parser.PubmedXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// PubmedArticle 解析策略。
///
/// 解析 PubMed Baseline XML 中的 `<PubmedArticle>` 元素，创建 `PubmedArticle` DTO。
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
/// - 本策略只负责提取 XML 数据，不做业务逻辑判断
/// - 解析结果为 `PubmedArticle` DTO，由 Processor 阶段进行 Venue 匹配
/// - 月份支持数字和英文缩写两种格式
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class PubmedArticleParsingStrategy implements RecordParsingStrategy<PubmedArticle> {

  /// 单例实例。
  public static final PubmedArticleParsingStrategy INSTANCE = new PubmedArticleParsingStrategy();

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

  private PubmedArticleParsingStrategy() {}

  @Override
  public String rootElementName() {
    return PubmedXmlElements.Container.PUBMED_ARTICLE;
  }

  /// 解析单个 PubmedArticle 元素。
  ///
  /// @param reader XML 流读取器（已定位到 PubmedArticle 元素）
  /// @param context 解析上下文（当前未使用）
  /// @return PubmedArticle DTO，缺少必填字段（PMID）时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public PubmedArticle parseRecord(XMLStreamReader reader, XmlParsingContext context)
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

    return buildArticle(fields);
  }

  // ========== 元素解析 ==========

  /// 解析起始元素。
  private void parseStartElement(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    String localName = reader.getLocalName();

    switch (localName) {
      case PubmedXmlElements.Container.MEDLINE_CITATION -> parseMedlineCitation(reader, fields);
      case PubmedXmlElements.Container.PUBMED_DATA -> parsePubmedData(reader, fields);
      default -> {
        // 跳过其他顶层元素
      }
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
          default -> {
            // 跳过其他元素
          }
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
          case PubmedXmlElements.Article.AUTHOR_LIST ->
              fields.authorsComplete =
                  XmlParsingHelper.parseYesNoAttributeNullable(
                      reader, PubmedXmlElements.Attribute.COMPLETE_YN);
          default -> {
            // 跳过其他元素
          }
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
          default -> {
            // 跳过其他元素
          }
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
          default -> {
            // 跳过其他元素
          }
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
          default -> {
            // 跳过其他元素
          }
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
          default -> {
            // 跳过其他元素
          }
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
          default -> {
            // 跳过其他元素
          }
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

  /// 构建 PubmedArticle DTO。
  private PubmedArticle buildArticle(ParsedFields fields) {
    return PubmedArticle.builder()
        .pmid(fields.pmid)
        .doi(fields.doi)
        .pmcId(fields.pmcId)
        .articleTitle(fields.articleTitle)
        .vernacularTitle(fields.vernacularTitle)
        .nlmUniqueId(fields.nlmUniqueId)
        .issnPrint(fields.issnPrint)
        .issnElectronic(fields.issnElectronic)
        .issnLinking(fields.issnLinking)
        .journalTitle(fields.journalTitle)
        .volume(fields.volume)
        .issue(fields.issue)
        .pubYear(fields.pubYear)
        .pubMonth(fields.pubMonth)
        .pubDay(fields.pubDay)
        .medlineDate(fields.medlineDate)
        .languages(fields.languages)
        .publicationStatus(fields.publicationStatus)
        .authorsComplete(fields.authorsComplete)
        .build();
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
  }
}
