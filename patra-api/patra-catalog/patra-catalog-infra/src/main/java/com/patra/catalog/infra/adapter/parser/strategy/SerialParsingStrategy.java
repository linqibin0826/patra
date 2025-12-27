package com.patra.catalog.infra.adapter.parser.strategy;

import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedBroadHeading;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedCrossReference;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedCurrentIndexing;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedGeneralNote;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedIndexingHistory;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedLanguage;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedMeshHeading;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedRecordId;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedTitleRelation;
import com.patra.catalog.infra.adapter.parser.LsiouXmlElements;
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

/// PubmedSerialData 解析策略。
///
/// 解析 NLM Serfile XML 中的 `<Serial>` 元素，直接创建 Domain 层的 `PubmedSerialData`。
/// 处理期刊基本信息、ISSN、出版信息、MeSH 主题词、期刊关联、索引信息等。
///
/// **架构说明**：
///
/// 此策略直接产出 Domain 模型，遵循「务实六边形架构」原则：
/// - 单向只读数据流（XML → Domain），无需中间 DTO 层
/// - 减少模型重复和转换开销
/// - 参考：Victor Rentea「仅在多通道暴露时才需 DTO」
///
/// **XML 结构**：
/// ```xml
/// <Serial Status="NLMCollection" PMC="Yes" MedPrintYN="N" DataCreationMethod="P">
///   <NlmWorkID>9876543</NlmWorkID>
///   <NlmUniqueID>0123456</NlmUniqueID>
///   <Title>Journal of Test Medicine</Title>
///   ...
/// </Serial>
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class SerialParsingStrategy implements RecordParsingStrategy<PubmedSerialData> {

  /// 单例实例。
  public static final SerialParsingStrategy INSTANCE = new SerialParsingStrategy();

  private SerialParsingStrategy() {}

  @Override
  public String rootElementName() {
    return LsiouXmlElements.Record.SERIAL;
  }

  /// 解析单个 Serial 元素。
  ///
  /// @param reader XML 流读取器（已定位到 Serial 元素）
  /// @param context 解析上下文
  /// @return PubmedSerialData 领域模型，缺少必填字段时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public PubmedSerialData parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    // === 1. 读取 Serial 元素属性（必须在进入子元素前读取） ===
    String status = reader.getAttributeValue(null, LsiouXmlElements.Attribute.STATUS);
    String pmc = reader.getAttributeValue(null, LsiouXmlElements.Attribute.PMC);
    Boolean medPrintYN =
        XmlParsingHelper.parseYesNoAttributeNullable(
            reader, LsiouXmlElements.Attribute.MED_PRINT_YN);
    String dataCreationMethod =
        reader.getAttributeValue(null, LsiouXmlElements.Attribute.DATA_CREATION_METHOD);

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
    List<PubmedCurrentIndexing> currentIndexings = new ArrayList<>();
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
    List<PubmedLanguage> languages = new ArrayList<>();
    List<PubmedBroadHeading> broadJournalHeadings = new ArrayList<>();
    List<PubmedCrossReference> crossReferences = new ArrayList<>();
    List<PubmedMeshHeading> meshHeadings = new ArrayList<>();
    List<PubmedTitleRelation> titleRelations = new ArrayList<>();
    List<PubmedIndexingHistory> indexingHistories = new ArrayList<>();
    List<PubmedGeneralNote> generalNotes = new ArrayList<>();

    // === 解析子元素 ===
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          // --- 标识符 ---
          case LsiouXmlElements.Identifier.NLM_UNIQUE_ID ->
              nlmUniqueId = reader.getElementText().trim();
          case LsiouXmlElements.Identifier.NLM_WORK_ID ->
              nlmWorkId = reader.getElementText().trim();
          case LsiouXmlElements.Identifier.CODEN -> coden = reader.getElementText().trim();

          // --- 名称 ---
          case LsiouXmlElements.Name.TITLE -> title = reader.getElementText().trim();
          case LsiouXmlElements.Name.MEDLINE_TA -> medlineTA = reader.getElementText().trim();
          case LsiouXmlElements.Name.SORT_SERIAL_NAME ->
              sortSerialName = reader.getElementText().trim();

          // --- ISSN ---
          case LsiouXmlElements.Issn.ISSN_LINKING -> issnL = reader.getElementText().trim();
          case LsiouXmlElements.Issn.ISSN -> {
            String issnType = reader.getAttributeValue(null, LsiouXmlElements.Attribute.ISSN_TYPE);
            String issnValue = reader.getElementText().trim();
            if (LsiouXmlElements.Value.ISSN_TYPE_PRINT.equals(issnType)) {
              issnPrint = issnValue;
            } else if (LsiouXmlElements.Value.ISSN_TYPE_ELECTRONIC.equals(issnType)) {
              issnElectronic = issnValue;
            }
          }

          // --- 出版信息 ---
          case LsiouXmlElements.Publication.PUBLICATION_INFO -> {
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
          case LsiouXmlElements.Language.LANGUAGE -> {
            String langType = reader.getAttributeValue(null, LsiouXmlElements.Attribute.LANG_TYPE);
            String langText = reader.getElementText().trim();
            if (!langText.isEmpty()) {
              boolean isPrimary = "Primary".equalsIgnoreCase(langType);
              languages.add(PubmedLanguage.of(langText, isPrimary));
            }
          }

          // --- 索引相关 ---
          case LsiouXmlElements.Indexing.CURRENTLY_INDEXED_YN ->
              currentlyIndexedYN = parseYesNo(reader.getElementText());
          case LsiouXmlElements.Indexing.CURRENTLY_INDEXED_FOR_SUBSET -> {
            PubmedCurrentIndexing indexing = parseCurrentlyIndexedForSubset(reader);
            if (indexing != null) {
              currentIndexings.add(indexing);
            }
          }
          case LsiouXmlElements.Indexing.INDEXING_SUBSET ->
              indexingSubset = reader.getElementText().trim();
          case LsiouXmlElements.Indexing.INDEXING_START_DATE ->
              indexingStartDate = reader.getElementText().trim();
          case LsiouXmlElements.Indexing.INDEX_ONLINE_YN ->
              indexOnlineYN = parseYesNo(reader.getElementText());
          case LsiouXmlElements.Indexing.INDEXING_SELECTED_URL ->
              indexingSelectedURL = reader.getElementText().trim();
          case LsiouXmlElements.Indexing.REPORTED_MEDLINE_YN ->
              reportedMedlineYN = parseYesNo(reader.getElementText());
          case LsiouXmlElements.Indexing.PROCESSING_CODE ->
              processingCode = reader.getElementText().trim();
          case LsiouXmlElements.Indexing.INDEXING_HISTORY_LIST ->
              indexingHistories = parseIndexingHistoryList(reader);

          // --- 分类和交叉引用 ---
          case LsiouXmlElements.MeSH.BROAD_JOURNAL_HEADING_LIST ->
              broadJournalHeadings = parseBroadJournalHeadingList(reader);
          case LsiouXmlElements.Relation.CROSS_REFERENCE_LIST ->
              crossReferences = parseCrossReferenceList(reader);
          case LsiouXmlElements.MeSH.MESH_HEADING_LIST ->
              meshHeadings = parseMeshHeadingList(reader);

          // --- 期刊关联 ---
          case LsiouXmlElements.Relation.TITLE_RELATED -> {
            PubmedTitleRelation relation = parseTitleRelated(reader);
            if (relation != null) {
              titleRelations.add(relation);
            }
          }

          // --- 备注 ---
          case LsiouXmlElements.Relation.GENERAL_NOTE -> {
            PubmedGeneralNote note = parseGeneralNote(reader);
            if (note != null) {
              generalNotes.add(note);
            }
          }

          // --- 标记字段 ---
          case LsiouXmlElements.Relation.TITLE_CONTINUATION_YN ->
              titleContinuationYN = parseYesNo(reader.getElementText());
          case LsiouXmlElements.Relation.MINOR_TITLE_CHANGE_YN ->
              minorTitleChangeYN = parseYesNo(reader.getElementText());

          // --- 时间戳 ---
          case LsiouXmlElements.Date.ILS_CREATED_TIMESTAMP ->
              ilsCreatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, LsiouXmlElements.Date.ILS_CREATED_TIMESTAMP);
          case LsiouXmlElements.Date.ILS_UPDATED_TIMESTAMP ->
              ilsUpdatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, LsiouXmlElements.Date.ILS_UPDATED_TIMESTAMP);
          case LsiouXmlElements.Date.DELETED_TIMESTAMP ->
              deletedTimestamp =
                  XmlParsingHelper.parseTimestamp(reader, LsiouXmlElements.Date.DELETED_TIMESTAMP);
          case LsiouXmlElements.Date.MEDLINE_DATA_UPDATED_TIMESTAMP ->
              medlineDataUpdatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, LsiouXmlElements.Date.MEDLINE_DATA_UPDATED_TIMESTAMP);
          case LsiouXmlElements.Date.SEF_CREATED_TIMESTAMP ->
              sefCreatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, LsiouXmlElements.Date.SEF_CREATED_TIMESTAMP);
          case LsiouXmlElements.Date.SEF_UPDATED_TIMESTAMP ->
              sefUpdatedTimestamp =
                  XmlParsingHelper.parseTimestamp(
                      reader, LsiouXmlElements.Date.SEF_UPDATED_TIMESTAMP);

          default -> {
            // 忽略未处理的元素
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.Record.SERIAL.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段
    if (nlmUniqueId == null || title == null) {
      log.warn("跳过无效 Serial（缺少必填字段）: NlmUniqueID={}, Title={}", nlmUniqueId, title);
      return null;
    }

    // 构建 PubmedSerialData（直接使用 Domain Builder）
    return PubmedSerialData.builder()
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
        .currentIndexings(currentIndexings)
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
          case LsiouXmlElements.Publication.COUNTRY -> country = reader.getElementText().trim();
          case LsiouXmlElements.Publication.PLACE -> places.add(reader.getElementText().trim());
          case LsiouXmlElements.Publication.PUBLISHER ->
              publishers.add(reader.getElementText().trim());
          case LsiouXmlElements.Publication.FREQUENCY -> {
            frequencyType =
                reader.getAttributeValue(null, LsiouXmlElements.Attribute.FREQUENCY_TYPE);
            frequency = reader.getElementText().trim();
          }
          case LsiouXmlElements.Publication.PUBLICATION_FIRST_YEAR ->
              firstYear = parseIntSafe(reader.getElementText());
          case LsiouXmlElements.Publication.PUBLICATION_END_YEAR ->
              endYear = parseIntSafe(reader.getElementText());
          case LsiouXmlElements.Publication.DATES_OF_SERIAL_PUBLICATION ->
              datesOfSerialPublication = reader.getElementText().trim();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.Publication.PUBLICATION_INFO.equals(reader.getLocalName())) {
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
  private PubmedCurrentIndexing parseCurrentlyIndexedForSubset(XMLStreamReader reader)
      throws XMLStreamException {
    String currentSubset =
        reader.getAttributeValue(null, LsiouXmlElements.Attribute.CURRENT_SUBSET);
    String currentIndexingTreatment =
        reader.getAttributeValue(null, LsiouXmlElements.Attribute.CURRENT_INDEXING_TREATMENT);
    String content = reader.getElementText().trim();

    return PubmedCurrentIndexing.of(currentSubset, currentIndexingTreatment, content);
  }

  // ========== 广泛期刊分类解析 ==========

  /// 解析 BroadJournalHeadingList 元素。
  private List<PubmedBroadHeading> parseBroadJournalHeadingList(XMLStreamReader reader)
      throws XMLStreamException {
    List<PubmedBroadHeading> headings = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && LsiouXmlElements.MeSH.BROAD_JOURNAL_HEADING.equals(reader.getLocalName())) {
        String heading = reader.getElementText().trim();
        if (!heading.isEmpty()) {
          headings.add(PubmedBroadHeading.of(heading));
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.MeSH.BROAD_JOURNAL_HEADING_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return headings;
  }

  // ========== 交叉引用解析 ==========

  /// 解析 CrossReferenceList 元素。
  private List<PubmedCrossReference> parseCrossReferenceList(XMLStreamReader reader)
      throws XMLStreamException {
    List<PubmedCrossReference> references = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && LsiouXmlElements.Relation.CROSS_REFERENCE.equals(reader.getLocalName())) {
        PubmedCrossReference ref = parseCrossReference(reader);
        if (ref != null) {
          references.add(ref);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.Relation.CROSS_REFERENCE_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return references;
  }

  /// 解析单个 CrossReference 元素。
  private PubmedCrossReference parseCrossReference(XMLStreamReader reader)
      throws XMLStreamException {
    String xrType = reader.getAttributeValue(null, LsiouXmlElements.Attribute.XR_TYPE);
    String xrTitle = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && LsiouXmlElements.Name.XR_TITLE.equals(reader.getLocalName())) {
        xrTitle = reader.getElementText().trim();
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.Relation.CROSS_REFERENCE.equals(reader.getLocalName())) {
        break;
      }
    }

    if (xrTitle == null || xrTitle.isEmpty()) {
      return null;
    }

    return PubmedCrossReference.of(xrType, xrTitle);
  }

  // ========== 备注解析 ==========

  /// 解析 GeneralNote 元素。
  private PubmedGeneralNote parseGeneralNote(XMLStreamReader reader) throws XMLStreamException {
    String noteType = reader.getAttributeValue(null, LsiouXmlElements.Attribute.NOTE_TYPE);
    String content = reader.getElementText().trim();

    if (content.isEmpty()) {
      return null;
    }

    return PubmedGeneralNote.of(noteType, content);
  }

  // ========== MeSH 主题词解析 ==========

  /// 解析 MeshHeadingList 元素。
  private List<PubmedMeshHeading> parseMeshHeadingList(XMLStreamReader reader)
      throws XMLStreamException {
    List<PubmedMeshHeading> headings = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && LsiouXmlElements.MeSH.MESH_HEADING.equals(reader.getLocalName())) {
        PubmedMeshHeading heading = parseMeshHeading(reader);
        if (heading != null) {
          headings.add(heading);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.MeSH.MESH_HEADING_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return headings;
  }

  /// 解析单个 MeshHeading 元素。
  private PubmedMeshHeading parseMeshHeading(XMLStreamReader reader) throws XMLStreamException {
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
          case LsiouXmlElements.MeSH.DESCRIPTOR_NAME -> {
            descriptorMajorTopic =
                XmlParsingHelper.parseYesNoAttribute(
                    reader, LsiouXmlElements.Attribute.MAJOR_TOPIC_YN, false);
            descriptorUi = reader.getAttributeValue(null, LsiouXmlElements.Attribute.UI);
            descriptorType = reader.getAttributeValue(null, LsiouXmlElements.Attribute.TYPE);
            descriptorName = reader.getElementText().trim();
          }
          case LsiouXmlElements.MeSH.QUALIFIER_NAME -> {
            qualifierMajorTopic =
                XmlParsingHelper.parseYesNoAttribute(
                    reader, LsiouXmlElements.Attribute.MAJOR_TOPIC_YN, false);
            qualifierName = reader.getElementText().trim();
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.MeSH.MESH_HEADING.equals(reader.getLocalName())) {
        break;
      }
    }

    if (descriptorName == null) {
      return null;
    }

    return PubmedMeshHeading.ofFull(
        descriptorName,
        descriptorUi,
        descriptorType,
        descriptorMajorTopic,
        qualifierName,
        qualifierMajorTopic);
  }

  // ========== 期刊关联解析 ==========

  /// 解析单个 TitleRelated 元素。
  private PubmedTitleRelation parseTitleRelated(XMLStreamReader reader) throws XMLStreamException {
    String titleType = reader.getAttributeValue(null, LsiouXmlElements.Attribute.TITLE_TYPE);
    String relatedTitle = null;
    String relatedIssn = null;
    List<PubmedRecordId> recordIds = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case LsiouXmlElements.Name.TITLE -> relatedTitle = reader.getElementText().trim();
          case LsiouXmlElements.Issn.ISSN -> relatedIssn = reader.getElementText().trim();
          case LsiouXmlElements.Identifier.RECORD_ID -> {
            String source = reader.getAttributeValue(null, LsiouXmlElements.Attribute.SOURCE);
            String id = reader.getElementText().trim();
            if (!id.isEmpty()) {
              recordIds.add(PubmedRecordId.of(id, source));
            }
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.Relation.TITLE_RELATED.equals(reader.getLocalName())) {
        break;
      }
    }

    if (titleType == null || relatedTitle == null) {
      return null;
    }

    return PubmedTitleRelation.ofFull(titleType, relatedTitle, relatedIssn, recordIds);
  }

  // ========== 索引历史解析 ==========

  /// 解析 IndexingHistoryList 元素。
  private List<PubmedIndexingHistory> parseIndexingHistoryList(XMLStreamReader reader)
      throws XMLStreamException {
    List<PubmedIndexingHistory> histories = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && LsiouXmlElements.Indexing.INDEXING_HISTORY.equals(reader.getLocalName())) {
        PubmedIndexingHistory history = parseIndexingHistory(reader);
        if (history != null) {
          histories.add(history);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.Indexing.INDEXING_HISTORY_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return histories;
  }

  /// 解析单个 IndexingHistory 元素。
  private PubmedIndexingHistory parseIndexingHistory(XMLStreamReader reader)
      throws XMLStreamException {
    // 从属性获取信息
    String citationSubset =
        reader.getAttributeValue(null, LsiouXmlElements.Attribute.CITATION_SUBSET);
    String indexingTreatment =
        reader.getAttributeValue(null, LsiouXmlElements.Attribute.INDEXING_TREATMENT);
    String indexingStatus =
        reader.getAttributeValue(null, LsiouXmlElements.Attribute.INDEXING_STATUS);

    LocalDate dateOfAction = null;
    String coverage = null;
    String coverageNote = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case LsiouXmlElements.Indexing.DATE_OF_ACTION ->
              dateOfAction =
                  XmlParsingHelper.parseDate(reader, LsiouXmlElements.Indexing.DATE_OF_ACTION);
          case LsiouXmlElements.Indexing.COVERAGE -> coverage = reader.getElementText().trim();
          case LsiouXmlElements.Indexing.COVERAGE_NOTE ->
              coverageNote = reader.getElementText().trim();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.Indexing.INDEXING_HISTORY.equals(reader.getLocalName())) {
        break;
      }
    }

    return PubmedIndexingHistory.ofFull(
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
      if (log.isDebugEnabled()) {
        log.debug("解析整数失败: {}", text);
      }
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
