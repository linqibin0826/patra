package com.patra.catalog.infra.adapter.parser.strategy;

import com.patra.catalog.infra.adapter.parser.SerfileXmlElements;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialBroadHeading;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialCrossReference;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialCurrentlyIndexedForSubset;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialGeneralNote;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialIndexingHistory;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialLanguage;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialMeshHeading;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialRecord;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialRecordId;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialTitleRelated;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingHelper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// SerialRecord 解析策略。
///
/// 解析 NLM Serfile XML 中的 `<Serial>` 元素，创建 `SerialRecord` DTO。
/// 处理期刊基本信息、ISSN、出版信息、MeSH 主题词、期刊关联、索引信息等。
///
/// **XML 结构**：
/// ```xml
/// <Serial Status="NLMCollection" PMC="Yes" MedPrintYN="N" DataCreationMethod="P">
///   <NlmWorkID>9876543</NlmWorkID>
///   <NlmUniqueID>0123456</NlmUniqueID>
///   <Title>Journal of Test Medicine</Title>
///   <MedlineTA>J Test Med</MedlineTA>
///   <SortSerialName>JOURNAL OF TEST MEDICINE</SortSerialName>
///   <PublicationInfo>...</PublicationInfo>
///   <ISSN IssnType="Print">1234-5678</ISSN>
///   <ISSNLinking>1234-5678</ISSNLinking>
///   <Language LangType="Primary">eng</Language>
///   <Coden>JTMED1</Coden>
///   <CurrentlyIndexedYN>Y</CurrentlyIndexedYN>
///   <CurrentlyIndexedForSubset CurrentSubset="IM" CurrentIndexingTreatment="Full"/>
///   <IndexingSubset>IM</IndexingSubset>
///   <BroadJournalHeadingList>...</BroadJournalHeadingList>
///   <CrossReferenceList>...</CrossReferenceList>
///   <MeshHeadingList>...</MeshHeadingList>
///   <TitleRelated>...</TitleRelated>
///   <IndexingHistoryList>...</IndexingHistoryList>
///   <GeneralNote>...</GeneralNote>
///   <IlsCreatedTimestamp>...</IlsCreatedTimestamp>
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
    // === 1. 读取 Serial 元素属性（必须在进入子元素前读取） ===
    String status = reader.getAttributeValue(null, SerfileXmlElements.Attribute.STATUS);
    String pmc = reader.getAttributeValue(null, SerfileXmlElements.Attribute.PMC);
    Boolean medPrintYN =
        XmlParsingHelper.parseYesNoAttributeNullable(
            reader, SerfileXmlElements.Attribute.MED_PRINT_YN);
    String dataCreationMethod =
        reader.getAttributeValue(null, SerfileXmlElements.Attribute.DATA_CREATION_METHOD);

    // === 2. 基本标识符 ===
    String nlmUniqueId = null;
    String nlmWorkId = null;
    String title = null;
    String medlineTA = null;
    String sortSerialName = null;
    String coden = null;

    // === 3. ISSN 信息 ===
    String issnL = null;
    String issnPrint = null;
    String issnElectronic = null;

    // === 4. 出版信息 ===
    String country = null;
    String frequency = null;
    String frequencyType = null;
    Integer publicationFirstYear = null;
    Integer publicationEndYear = null;
    String datesOfSerialPublication = null;
    List<String> places = new ArrayList<>();
    List<String> publishers = new ArrayList<>();

    // === 5. 索引相关 ===
    Boolean currentlyIndexedYN = null;
    List<SerialCurrentlyIndexedForSubset> currentlyIndexedForSubsets = new ArrayList<>();
    String indexingSubset = null;
    String indexingStartDate = null;
    Boolean indexOnlineYN = null;
    String indexingSelectedURL = null;
    Boolean reportedMedlineYN = null;
    String processingCode = null;

    // === 6. 标记字段 ===
    Boolean titleContinuationYN = null;
    Boolean minorTitleChangeYN = null;

    // === 7. 时间戳 ===
    LocalDateTime ilsCreatedTimestamp = null;
    LocalDateTime ilsUpdatedTimestamp = null;
    LocalDateTime deletedTimestamp = null;
    LocalDateTime medlineDataUpdatedTimestamp = null;
    LocalDateTime sefCreatedTimestamp = null;
    LocalDateTime sefUpdatedTimestamp = null;

    // === 8. 集合字段 ===
    List<SerialLanguage> languages = new ArrayList<>();
    List<SerialBroadHeading> broadJournalHeadings = new ArrayList<>();
    List<SerialCrossReference> crossReferences = new ArrayList<>();
    List<SerialMeshHeading> meshHeadings = new ArrayList<>();
    List<SerialTitleRelated> titleRelations = new ArrayList<>();
    List<SerialIndexingHistory> indexingHistories = new ArrayList<>();
    List<SerialGeneralNote> generalNotes = new ArrayList<>();

    // === 解析子元素 ===
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          // --- 标识符 ---
          case SerfileXmlElements.Identifier.NLM_UNIQUE_ID ->
              nlmUniqueId = reader.getElementText().trim();
          case SerfileXmlElements.Identifier.NLM_WORK_ID ->
              nlmWorkId = reader.getElementText().trim();
          case SerfileXmlElements.Identifier.CODEN -> coden = reader.getElementText().trim();

          // --- 名称 ---
          case SerfileXmlElements.Name.TITLE -> title = reader.getElementText().trim();
          case SerfileXmlElements.Name.MEDLINE_TA -> medlineTA = reader.getElementText().trim();
          case SerfileXmlElements.Name.SORT_SERIAL_NAME ->
              sortSerialName = reader.getElementText().trim();

          // --- ISSN ---
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

          // --- 出版信息 ---
          case SerfileXmlElements.Publication.PUBLICATION_INFO -> {
            var pubInfo = parsePublicationInfo(reader);
            country = pubInfo.country();
            frequency = pubInfo.frequency();
            frequencyType = pubInfo.frequencyType();
            publicationFirstYear = pubInfo.firstYear();
            publicationEndYear = pubInfo.endYear();
            datesOfSerialPublication = pubInfo.datesOfSerialPublication();
            places = pubInfo.places();
            publishers = pubInfo.publishers();
          }

          // --- 语言 ---
          case SerfileXmlElements.Language.LANGUAGE -> {
            String langType =
                reader.getAttributeValue(null, SerfileXmlElements.Attribute.LANG_TYPE);
            String langText = reader.getElementText().trim();
            if (!langText.isEmpty()) {
              languages.add(SerialLanguage.of(langText, langType));
            }
          }

          // --- 索引相关 ---
          case SerfileXmlElements.Indexing.CURRENTLY_INDEXED_YN ->
              currentlyIndexedYN = parseYesNo(reader.getElementText());
          case SerfileXmlElements.Indexing.CURRENTLY_INDEXED_FOR_SUBSET -> {
            SerialCurrentlyIndexedForSubset subset = parseCurrentlyIndexedForSubset(reader);
            if (subset != null) {
              currentlyIndexedForSubsets.add(subset);
            }
          }
          case SerfileXmlElements.Indexing.INDEXING_SUBSET ->
              indexingSubset = reader.getElementText().trim();
          case SerfileXmlElements.Indexing.INDEXING_START_DATE ->
              indexingStartDate = reader.getElementText().trim();
          case SerfileXmlElements.Indexing.INDEX_ONLINE_YN ->
              indexOnlineYN = parseYesNo(reader.getElementText());
          case SerfileXmlElements.Indexing.INDEXING_SELECTED_URL ->
              indexingSelectedURL = reader.getElementText().trim();
          case SerfileXmlElements.Indexing.REPORTED_MEDLINE_YN ->
              reportedMedlineYN = parseYesNo(reader.getElementText());
          case SerfileXmlElements.Indexing.PROCESSING_CODE ->
              processingCode = reader.getElementText().trim();
          case SerfileXmlElements.Indexing.INDEXING_HISTORY_LIST ->
              indexingHistories = parseIndexingHistoryList(reader);

          // --- 分类和交叉引用 ---
          case SerfileXmlElements.MeSH.BROAD_JOURNAL_HEADING_LIST ->
              broadJournalHeadings = parseBroadJournalHeadingList(reader);
          case SerfileXmlElements.Relation.CROSS_REFERENCE_LIST ->
              crossReferences = parseCrossReferenceList(reader);
          case SerfileXmlElements.MeSH.MESH_HEADING_LIST ->
              meshHeadings = parseMeshHeadingList(reader);

          // --- 期刊关联 ---
          case SerfileXmlElements.Relation.TITLE_RELATED -> {
            SerialTitleRelated relation = parseTitleRelated(reader);
            if (relation != null) {
              titleRelations.add(relation);
            }
          }

          // --- 备注 ---
          case SerfileXmlElements.Relation.GENERAL_NOTE -> {
            SerialGeneralNote note = parseGeneralNote(reader);
            if (note != null) {
              generalNotes.add(note);
            }
          }

          // --- 标记字段 ---
          case SerfileXmlElements.Relation.TITLE_CONTINUATION_YN ->
              titleContinuationYN = parseYesNo(reader.getElementText());
          case SerfileXmlElements.Relation.MINOR_TITLE_CHANGE_YN ->
              minorTitleChangeYN = parseYesNo(reader.getElementText());

          // --- 时间戳 ---
          case SerfileXmlElements.Date.ILS_CREATED_TIMESTAMP ->
              ilsCreatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, SerfileXmlElements.Date.ILS_CREATED_TIMESTAMP);
          case SerfileXmlElements.Date.ILS_UPDATED_TIMESTAMP ->
              ilsUpdatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, SerfileXmlElements.Date.ILS_UPDATED_TIMESTAMP);
          case SerfileXmlElements.Date.DELETED_TIMESTAMP ->
              deletedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, SerfileXmlElements.Date.DELETED_TIMESTAMP);
          case SerfileXmlElements.Date.MEDLINE_DATA_UPDATED_TIMESTAMP ->
              medlineDataUpdatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, SerfileXmlElements.Date.MEDLINE_DATA_UPDATED_TIMESTAMP);
          case SerfileXmlElements.Date.SEF_CREATED_TIMESTAMP ->
              sefCreatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, SerfileXmlElements.Date.SEF_CREATED_TIMESTAMP);
          case SerfileXmlElements.Date.SEF_UPDATED_TIMESTAMP ->
              sefUpdatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, SerfileXmlElements.Date.SEF_UPDATED_TIMESTAMP);

          default -> {
            // 忽略未处理的元素
          }
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
        // 基本标识符
        .nlmUniqueId(nlmUniqueId)
        .nlmWorkId(nlmWorkId)
        .title(title)
        .medlineTA(medlineTA)
        .sortSerialName(sortSerialName)
        // ISSN 和 CODEN
        .issnL(issnL)
        .issnPrint(issnPrint)
        .issnElectronic(issnElectronic)
        .coden(coden)
        // Serial 属性
        .status(status)
        .pmc(pmc)
        .medPrintYN(medPrintYN)
        .dataCreationMethod(dataCreationMethod)
        // 出版信息
        .country(country)
        .frequency(frequency)
        .frequencyType(frequencyType)
        .publicationFirstYear(publicationFirstYear)
        .publicationEndYear(publicationEndYear)
        .datesOfSerialPublication(datesOfSerialPublication)
        .places(places)
        .publishers(publishers)
        // 语言
        .languages(languages)
        // 索引相关
        .currentlyIndexedYN(currentlyIndexedYN)
        .currentlyIndexedForSubsets(currentlyIndexedForSubsets)
        .indexingSubset(indexingSubset)
        .indexingStartDate(indexingStartDate)
        .indexOnlineYN(indexOnlineYN)
        .indexingSelectedURL(indexingSelectedURL)
        .reportedMedlineYN(reportedMedlineYN)
        .processingCode(processingCode)
        // 分类和关联
        .broadJournalHeadings(broadJournalHeadings)
        .crossReferences(crossReferences)
        .meshHeadings(meshHeadings)
        .titleRelations(titleRelations)
        .indexingHistories(indexingHistories)
        // 备注
        .generalNotes(generalNotes)
        // 标记字段
        .titleContinuationYN(titleContinuationYN)
        .minorTitleChangeYN(minorTitleChangeYN)
        // 时间戳
        .ilsCreatedTimestamp(ilsCreatedTimestamp)
        .ilsUpdatedTimestamp(ilsUpdatedTimestamp)
        .deletedTimestamp(deletedTimestamp)
        .medlineDataUpdatedTimestamp(medlineDataUpdatedTimestamp)
        .sefCreatedTimestamp(sefCreatedTimestamp)
        .sefUpdatedTimestamp(sefUpdatedTimestamp)
        .build();
  }

  // ========== 出版信息解析 ==========

  /// 解析 PublicationInfo 元素。
  private PublicationInfoResult parsePublicationInfo(XMLStreamReader reader)
      throws XMLStreamException {
    String country = null;
    String frequency = null;
    String frequencyType = null;
    Integer firstYear = null;
    Integer endYear = null;
    String datesOfSerialPublication = null;
    List<String> places = new ArrayList<>();
    List<String> publishers = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case SerfileXmlElements.Publication.COUNTRY -> country = reader.getElementText().trim();
          case SerfileXmlElements.Publication.PLACE -> places.add(reader.getElementText().trim());
          case SerfileXmlElements.Publication.PUBLISHER ->
              publishers.add(reader.getElementText().trim());
          case SerfileXmlElements.Publication.FREQUENCY -> {
            frequencyType =
                reader.getAttributeValue(null, SerfileXmlElements.Attribute.FREQUENCY_TYPE);
            frequency = reader.getElementText().trim();
          }
          case SerfileXmlElements.Publication.PUBLICATION_FIRST_YEAR ->
              firstYear = parseIntSafe(reader.getElementText());
          case SerfileXmlElements.Publication.PUBLICATION_END_YEAR ->
              endYear = parseIntSafe(reader.getElementText());
          case SerfileXmlElements.Publication.DATES_OF_SERIAL_PUBLICATION ->
              datesOfSerialPublication = reader.getElementText().trim();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Publication.PUBLICATION_INFO.equals(reader.getLocalName())) {
        break;
      }
    }

    return new PublicationInfoResult(
        country,
        frequency,
        frequencyType,
        firstYear,
        endYear,
        datesOfSerialPublication,
        places,
        publishers);
  }

  /// 出版信息解析结果。
  private record PublicationInfoResult(
      String country,
      String frequency,
      String frequencyType,
      Integer firstYear,
      Integer endYear,
      String datesOfSerialPublication,
      List<String> places,
      List<String> publishers) {}

  // ========== 索引子集解析 ==========

  /// 解析 CurrentlyIndexedForSubset 元素。
  private SerialCurrentlyIndexedForSubset parseCurrentlyIndexedForSubset(XMLStreamReader reader)
      throws XMLStreamException {
    String currentSubset =
        reader.getAttributeValue(null, SerfileXmlElements.Attribute.CURRENT_SUBSET);
    String currentIndexingTreatment =
        reader.getAttributeValue(null, SerfileXmlElements.Attribute.CURRENT_INDEXING_TREATMENT);
    String content = reader.getElementText().trim();

    return SerialCurrentlyIndexedForSubset.of(content, currentSubset, currentIndexingTreatment);
  }

  // ========== 广泛期刊分类解析 ==========

  /// 解析 BroadJournalHeadingList 元素。
  private List<SerialBroadHeading> parseBroadJournalHeadingList(XMLStreamReader reader)
      throws XMLStreamException {
    List<SerialBroadHeading> headings = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && SerfileXmlElements.MeSH.BROAD_JOURNAL_HEADING.equals(reader.getLocalName())) {
        String heading = reader.getElementText().trim();
        if (!heading.isEmpty()) {
          headings.add(SerialBroadHeading.of(heading));
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.MeSH.BROAD_JOURNAL_HEADING_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return headings;
  }

  // ========== 交叉引用解析 ==========

  /// 解析 CrossReferenceList 元素。
  private List<SerialCrossReference> parseCrossReferenceList(XMLStreamReader reader)
      throws XMLStreamException {
    List<SerialCrossReference> references = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && SerfileXmlElements.Relation.CROSS_REFERENCE.equals(reader.getLocalName())) {
        SerialCrossReference ref = parseCrossReference(reader);
        if (ref != null) {
          references.add(ref);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Relation.CROSS_REFERENCE_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return references;
  }

  /// 解析单个 CrossReference 元素。
  private SerialCrossReference parseCrossReference(XMLStreamReader reader)
      throws XMLStreamException {
    String xrType = reader.getAttributeValue(null, SerfileXmlElements.Attribute.XR_TYPE);
    String xrTitle = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && SerfileXmlElements.Name.XR_TITLE.equals(reader.getLocalName())) {
        xrTitle = reader.getElementText().trim();
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Relation.CROSS_REFERENCE.equals(reader.getLocalName())) {
        break;
      }
    }

    if (xrTitle == null || xrTitle.isEmpty()) {
      return null;
    }

    return SerialCrossReference.of(xrType, xrTitle);
  }

  // ========== 备注解析 ==========

  /// 解析 GeneralNote 元素。
  private SerialGeneralNote parseGeneralNote(XMLStreamReader reader) throws XMLStreamException {
    String noteType = reader.getAttributeValue(null, SerfileXmlElements.Attribute.NOTE_TYPE);
    String content = reader.getElementText().trim();

    if (content.isEmpty()) {
      return null;
    }

    return SerialGeneralNote.of(noteType, content);
  }

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
    String descriptorUi = null;
    String descriptorType = null;
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
            descriptorUi = reader.getAttributeValue(null, SerfileXmlElements.Attribute.UI);
            descriptorType = reader.getAttributeValue(null, SerfileXmlElements.Attribute.TYPE);
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

    return SerialMeshHeading.ofFull(
        descriptorName,
        descriptorMajorTopic,
        descriptorUi,
        descriptorType,
        qualifierName,
        qualifierMajorTopic);
  }

  // ========== 期刊关联解析 ==========

  /// 解析单个 TitleRelated 元素。
  private SerialTitleRelated parseTitleRelated(XMLStreamReader reader) throws XMLStreamException {
    String titleType = reader.getAttributeValue(null, SerfileXmlElements.Attribute.TITLE_TYPE);
    String relatedTitle = null;
    String relatedIssn = null;
    List<SerialRecordId> recordIds = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case SerfileXmlElements.Name.TITLE -> relatedTitle = reader.getElementText().trim();
          case SerfileXmlElements.Issn.ISSN -> relatedIssn = reader.getElementText().trim();
          case SerfileXmlElements.Identifier.RECORD_ID -> {
            String source = reader.getAttributeValue(null, SerfileXmlElements.Attribute.SOURCE);
            String id = reader.getElementText().trim();
            if (!id.isEmpty()) {
              recordIds.add(SerialRecordId.of(id, source));
            }
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && SerfileXmlElements.Relation.TITLE_RELATED.equals(reader.getLocalName())) {
        break;
      }
    }

    if (titleType == null || relatedTitle == null) {
      return null;
    }

    return SerialTitleRelated.ofFull(titleType, relatedTitle, relatedIssn, recordIds);
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

  /// 解析 Y/N 字符串为 Boolean。
  private Boolean parseYesNo(String text) {
    if (text == null || text.trim().isEmpty()) {
      return null;
    }
    String trimmed = text.trim().toUpperCase();
    if ("Y".equals(trimmed) || "YES".equals(trimmed)) {
      return true;
    } else if ("N".equals(trimmed) || "NO".equals(trimmed)) {
      return false;
    }
    return null;
  }
}
