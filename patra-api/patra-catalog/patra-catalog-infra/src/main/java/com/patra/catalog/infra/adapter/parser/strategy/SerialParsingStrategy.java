package com.patra.catalog.infra.adapter.parser.strategy;

import com.patra.catalog.domain.model.dto.serfile.SerialIndexingHistory;
import com.patra.catalog.domain.model.dto.serfile.SerialMeshHeading;
import com.patra.catalog.domain.model.dto.serfile.SerialRecord;
import com.patra.catalog.domain.model.dto.serfile.SerialTitleRelated;
import com.patra.catalog.infra.adapter.parser.SerfileXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingHelper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// SerialRecord 解析策略。
///
/// 解析 NLM Serfile XML 中的 `<Serial>` 元素，创建 `SerialRecord` DTO。
/// 处理期刊基本信息、ISSN、出版信息、MeSH 主题词、期刊关联等。
///
/// **XML 结构**：
/// ```xml
/// <Serial Status="NLMCollection" PMC="Yes">
///   <NlmWorkID>9876543</NlmWorkID>
///   <NlmUniqueID>0123456</NlmUniqueID>
///   <Title>Journal of Test Medicine</Title>
///   <MedlineTA>J Test Med</MedlineTA>
///   <PublicationInfo>...</PublicationInfo>
///   <ISSN IssnType="Print">1234-5678</ISSN>
///   <ISSN IssnType="Electronic">1234-5679</ISSN>
///   <ISSNLinking>1234-5678</ISSNLinking>
///   <Language LangType="Primary">eng</Language>
///   <Coden>JTMED1</Coden>
///   <IndexingHistoryList>...</IndexingHistoryList>
///   <MeshHeadingList>...</MeshHeadingList>
///   <TitleRelated>...</TitleRelated>
/// </Serial>
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class SerialParsingStrategy implements RecordParsingStrategy<SerialRecord> {

  /// 单例实例。
  public static final SerialParsingStrategy INSTANCE = new SerialParsingStrategy();

  private SerialParsingStrategy() {}

  @Override
  public String rootElementName() {
    return SerfileXmlElements.Record.SERIAL;
  }

  /// 解析单个 Serial 元素。
  ///
  /// @param reader XML 流读取器（已定位到 Serial 元素）
  /// @param context 解析上下文
  /// @return SerialRecord DTO，缺少必填字段时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public SerialRecord parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    // 基本字段
    String nlmUniqueId = null;
    String title = null;
    String medlineTA = null;
    String coden = null;

    // ISSN 信息
    String issnL = null;
    String issnPrint = null;
    String issnElectronic = null;

    // 出版信息
    String country = null;
    String frequency = null;
    Integer publicationFirstYear = null;
    Integer publicationEndYear = null;

    // 集合字段
    List<String> languages = new ArrayList<>();
    List<SerialMeshHeading> meshHeadings = new ArrayList<>();
    List<SerialTitleRelated> titleRelations = new ArrayList<>();
    List<SerialIndexingHistory> indexingHistories = new ArrayList<>();

    // 解析子元素
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case SerfileXmlElements.Identifier.NLM_UNIQUE_ID ->
              nlmUniqueId = reader.getElementText().trim();
          case SerfileXmlElements.Name.TITLE -> title = reader.getElementText().trim();
          case SerfileXmlElements.Name.MEDLINE_TA -> medlineTA = reader.getElementText().trim();
          case SerfileXmlElements.Identifier.CODEN -> coden = reader.getElementText().trim();
          case SerfileXmlElements.Issn.ISSN_LINKING -> issnL = reader.getElementText().trim();
          case SerfileXmlElements.Issn.ISSN -> {
            String issnType =
                reader.getAttributeValue(null, SerfileXmlElements.Attribute.ISSN_TYPE);
            String issnValue = reader.getElementText().trim();
            if (SerfileXmlElements.Value.ISSN_TYPE_PRINT.equals(issnType)) {
              issnPrint = issnValue;
            } else if (SerfileXmlElements.Value.ISSN_TYPE_ELECTRONIC.equals(issnType)) {
              issnElectronic = issnValue;
            }
          }
          case SerfileXmlElements.Publication.PUBLICATION_INFO -> {
            var pubInfo = parsePublicationInfo(reader);
            country = pubInfo.country();
            frequency = pubInfo.frequency();
            publicationFirstYear = pubInfo.firstYear();
            publicationEndYear = pubInfo.endYear();
          }
          case SerfileXmlElements.Language.LANGUAGE -> {
            String langText = reader.getElementText().trim();
            if (!langText.isEmpty()) {
              languages.add(langText);
            }
          }
          case SerfileXmlElements.MeSH.MESH_HEADING_LIST ->
              meshHeadings = parseMeshHeadingList(reader);
          case SerfileXmlElements.Relation.TITLE_RELATED -> {
            SerialTitleRelated relation = parseTitleRelated(reader);
            if (relation != null) {
              titleRelations.add(relation);
            }
          }
          case SerfileXmlElements.Indexing.INDEXING_HISTORY_LIST ->
              indexingHistories = parseIndexingHistoryList(reader);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Record.SERIAL.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段
    if (nlmUniqueId == null || title == null) {
      log.warn("跳过无效 Serial（缺少必填字段）: NlmUniqueID={}, Title={}", nlmUniqueId, title);
      return null;
    }

    // 构建 SerialRecord
    return SerialRecord.builder()
        .nlmUniqueId(nlmUniqueId)
        .title(title)
        .medlineTA(medlineTA)
        .coden(coden)
        .issnL(issnL)
        .issnPrint(issnPrint)
        .issnElectronic(issnElectronic)
        .country(country)
        .frequency(frequency)
        .publicationFirstYear(publicationFirstYear)
        .publicationEndYear(publicationEndYear)
        .languages(languages)
        .meshHeadings(meshHeadings)
        .titleRelations(titleRelations)
        .indexingHistories(indexingHistories)
        .build();
  }

  // ========== 出版信息解析 ==========

  /// 解析 PublicationInfo 元素。
  private PublicationInfoResult parsePublicationInfo(XMLStreamReader reader)
      throws XMLStreamException {
    String country = null;
    String frequency = null;
    Integer firstYear = null;
    Integer endYear = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case SerfileXmlElements.Publication.COUNTRY -> country = reader.getElementText().trim();
          case SerfileXmlElements.Publication.FREQUENCY ->
              frequency = reader.getElementText().trim();
          case SerfileXmlElements.Publication.PUBLICATION_FIRST_YEAR ->
              firstYear = parseIntSafe(reader.getElementText());
          case SerfileXmlElements.Publication.PUBLICATION_END_YEAR ->
              endYear = parseIntSafe(reader.getElementText());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Publication.PUBLICATION_INFO.equals(reader.getLocalName())) {
        break;
      }
    }

    return new PublicationInfoResult(country, frequency, firstYear, endYear);
  }

  /// 出版信息解析结果。
  private record PublicationInfoResult(
      String country, String frequency, Integer firstYear, Integer endYear) {}

  // ========== MeSH 主题词解析 ==========

  /// 解析 MeshHeadingList 元素。
  private List<SerialMeshHeading> parseMeshHeadingList(XMLStreamReader reader)
      throws XMLStreamException {
    List<SerialMeshHeading> headings = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && SerfileXmlElements.MeSH.MESH_HEADING.equals(reader.getLocalName())) {
        SerialMeshHeading heading = parseMeshHeading(reader);
        if (heading != null) {
          headings.add(heading);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.MeSH.MESH_HEADING_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return headings;
  }

  /// 解析单个 MeshHeading 元素。
  private SerialMeshHeading parseMeshHeading(XMLStreamReader reader) throws XMLStreamException {
    String descriptorName = null;
    boolean descriptorMajorTopic = false;
    String qualifierName = null;
    boolean qualifierMajorTopic = false;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case SerfileXmlElements.MeSH.DESCRIPTOR_NAME -> {
            descriptorMajorTopic =
                XmlParsingHelper.parseYesNoAttribute(
                    reader, SerfileXmlElements.Attribute.MAJOR_TOPIC_YN, false);
            descriptorName = reader.getElementText().trim();
          }
          case SerfileXmlElements.MeSH.QUALIFIER_NAME -> {
            qualifierMajorTopic =
                XmlParsingHelper.parseYesNoAttribute(
                    reader, SerfileXmlElements.Attribute.MAJOR_TOPIC_YN, false);
            qualifierName = reader.getElementText().trim();
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.MeSH.MESH_HEADING.equals(reader.getLocalName())) {
        break;
      }
    }

    if (descriptorName == null) {
      return null;
    }

    return new SerialMeshHeading(
        descriptorName, descriptorMajorTopic, qualifierName, qualifierMajorTopic);
  }

  // ========== 期刊关联解析 ==========

  /// 解析单个 TitleRelated 元素。
  private SerialTitleRelated parseTitleRelated(XMLStreamReader reader) throws XMLStreamException {
    String titleType = reader.getAttributeValue(null, SerfileXmlElements.Attribute.TITLE_TYPE);
    String relatedTitle = null;
    String relatedIssn = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case SerfileXmlElements.Name.TITLE -> relatedTitle = reader.getElementText().trim();
          case SerfileXmlElements.Issn.ISSN -> relatedIssn = reader.getElementText().trim();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Relation.TITLE_RELATED.equals(reader.getLocalName())) {
        break;
      }
    }

    if (titleType == null || relatedTitle == null) {
      return null;
    }

    return new SerialTitleRelated(titleType, relatedTitle, relatedIssn);
  }

  // ========== 索引历史解析 ==========

  /// 解析 IndexingHistoryList 元素。
  private List<SerialIndexingHistory> parseIndexingHistoryList(XMLStreamReader reader)
      throws XMLStreamException {
    List<SerialIndexingHistory> histories = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && SerfileXmlElements.Indexing.INDEXING_HISTORY.equals(reader.getLocalName())) {
        SerialIndexingHistory history = parseIndexingHistory(reader);
        if (history != null) {
          histories.add(history);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Indexing.INDEXING_HISTORY_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return histories;
  }

  /// 解析单个 IndexingHistory 元素。
  private SerialIndexingHistory parseIndexingHistory(XMLStreamReader reader)
      throws XMLStreamException {
    // 从属性获取信息
    String citationSubset =
        reader.getAttributeValue(null, SerfileXmlElements.Attribute.CITATION_SUBSET);
    String indexingTreatment =
        reader.getAttributeValue(null, SerfileXmlElements.Attribute.INDEXING_TREATMENT);
    String indexingStatus =
        reader.getAttributeValue(null, SerfileXmlElements.Attribute.INDEXING_STATUS);

    LocalDate dateOfAction = null;
    String coverage = null;
    String coverageNote = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case SerfileXmlElements.Indexing.DATE_OF_ACTION ->
              dateOfAction =
                  XmlParsingHelper.parseDate(reader, SerfileXmlElements.Indexing.DATE_OF_ACTION);
          case SerfileXmlElements.Indexing.COVERAGE -> coverage = reader.getElementText().trim();
          case SerfileXmlElements.Indexing.COVERAGE_NOTE ->
              coverageNote = reader.getElementText().trim();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Indexing.INDEXING_HISTORY.equals(reader.getLocalName())) {
        break;
      }
    }

    return new SerialIndexingHistory(
        citationSubset, indexingTreatment, indexingStatus, dateOfAction, coverage, coverageNote);
  }

  // ========== 辅助方法 ==========

  /// 安全解析整数。
  private Integer parseIntSafe(String text) {
    if (text == null || text.trim().isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      log.warn("解析整数失败: {}", text);
      return null;
    }
  }
}
