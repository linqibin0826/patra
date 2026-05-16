package dev.linqibin.patra.catalog.infra.parser.strategy;

import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedBroadHeading;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedCrossReference;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedCurrentIndexing;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedGeneralNote;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedIndexingHistory;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedLanguage;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedMeshHeading;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedRecordId;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedTitleRelation;
import dev.linqibin.patra.catalog.infra.parser.LsiouXmlElements;
import dev.linqibin.patra.catalog.infra.parser.support.XmlParsingContext;
import dev.linqibin.patra.catalog.infra.parser.support.XmlParsingHelper;
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
/// **设计模式**：
///
/// 采用「解析上下文」模式，将 30+ 局部变量封装为结构化的中间数据持有者：
/// - `SerialParsingState` - 主解析状态，聚合所有中间数据
/// - 按职责分组：标识符、名称、ISSN、索引、时间戳、标记、集合
/// - 减少认知负担，提高代码可维护性
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

  // ========== 解析状态数据持有者 ==========

  /// Serial 元素属性。
  private record SerialAttributes(
      String status, String pmc, Boolean medPrintYN, String dataCreationMethod) {

    static SerialAttributes from(XMLStreamReader reader) {
      return new SerialAttributes(
          reader.getAttributeValue(null, LsiouXmlElements.Attribute.STATUS),
          reader.getAttributeValue(null, LsiouXmlElements.Attribute.PMC),
          XmlParsingHelper.parseYesNoAttributeNullable(
              reader, LsiouXmlElements.Attribute.MED_PRINT_YN),
          reader.getAttributeValue(null, LsiouXmlElements.Attribute.DATA_CREATION_METHOD));
    }
  }

  /// 标识符数据。
  private static final class IdentifierData {
    String nlmUniqueId;
    String nlmWorkId;
    String coden;
  }

  /// 名称数据。
  private static final class NameData {
    String title;
    String medlineTA;
    String sortSerialName;
  }

  /// ISSN 数据。
  private static final class IssnData {
    String issnL;
    String issnPrint;
    String issnElectronic;
  }

  /// 索引相关数据。
  private static final class IndexingData {
    Boolean currentlyIndexedYN;
    final List<PubmedCurrentIndexing> currentIndexings = new ArrayList<>();
    String indexingSubset;
    String indexingStartDate;
    Boolean indexOnlineYN;
    String indexingSelectedURL;
    Boolean reportedMedlineYN;
    String processingCode;
    final List<PubmedIndexingHistory> indexingHistories = new ArrayList<>();
  }

  /// 时间戳数据。
  private static final class TimestampData {
    LocalDateTime ilsCreatedTimestamp;
    LocalDateTime ilsUpdatedTimestamp;
    LocalDateTime deletedTimestamp;
    LocalDateTime medlineDataUpdatedTimestamp;
    LocalDateTime sefCreatedTimestamp;
    LocalDateTime sefUpdatedTimestamp;
  }

  /// 标记字段数据。
  private static final class MarkerData {
    Boolean titleContinuationYN;
    Boolean minorTitleChangeYN;
  }

  /// 集合字段数据。
  private static final class CollectionData {
    final List<PubmedLanguage> languages = new ArrayList<>();
    final List<PubmedBroadHeading> broadJournalHeadings = new ArrayList<>();
    final List<PubmedCrossReference> crossReferences = new ArrayList<>();
    final List<PubmedMeshHeading> meshHeadings = new ArrayList<>();
    final List<PubmedTitleRelation> titleRelations = new ArrayList<>();
    final List<PubmedGeneralNote> generalNotes = new ArrayList<>();
  }

  /// Serial 解析状态 - 聚合所有中间数据。
  private static final class SerialParsingState {
    final SerialAttributes attributes;
    final IdentifierData identifiers = new IdentifierData();
    final NameData names = new NameData();
    final IssnData issn = new IssnData();
    final IndexingData indexing = new IndexingData();
    final TimestampData timestamps = new TimestampData();
    final MarkerData markers = new MarkerData();
    final CollectionData collections = new CollectionData();
    PublicationInfoResult publication;

    SerialParsingState(SerialAttributes attributes) {
      this.attributes = attributes;
    }

    boolean isValid() {
      return identifiers.nlmUniqueId != null && names.title != null;
    }
  }

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
    // 1. 读取 Serial 元素属性并初始化解析状态
    var state = new SerialParsingState(SerialAttributes.from(reader));

    // 2. 解析子元素
    parseChildElements(reader, state);

    // 3. 验证并构建结果
    if (!state.isValid()) {
      log.warn(
          "跳过无效 Serial（缺少必填字段）: NlmUniqueID={}, Title={}",
          state.identifiers.nlmUniqueId,
          state.names.title);
      return null;
    }

    return buildPubmedSerialData(state);
  }

  // ========== 子元素解析 ==========

  /// 解析所有子元素，将结果存入解析状态。
  private void parseChildElements(XMLStreamReader reader, SerialParsingState state)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        handleStartElement(reader, state);
      } else if (event == XMLStreamConstants.END_ELEMENT
          && LsiouXmlElements.Record.SERIAL.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 处理开始元素事件。
  private void handleStartElement(XMLStreamReader reader, SerialParsingState state)
      throws XMLStreamException {
    String localName = reader.getLocalName();

    // 按类别分派处理
    if (handleIdentifierElement(localName, reader, state.identifiers)) return;
    if (handleNameElement(localName, reader, state.names)) return;
    if (handleIssnElement(localName, reader, state.issn)) return;
    if (handlePublicationElement(localName, reader, state)) return;
    if (handleLanguageElement(localName, reader, state.collections)) return;
    if (handleIndexingElement(localName, reader, state.indexing)) return;
    if (handleClassificationElement(localName, reader, state.collections)) return;
    if (handleRelationElement(localName, reader, state.collections)) return;
    if (handleMarkerElement(localName, reader, state.markers)) return;
    handleTimestampElement(localName, reader, state.timestamps);
  }

  // ========== 标识符元素处理 ==========

  /// 处理标识符元素。
  private boolean handleIdentifierElement(
      String localName, XMLStreamReader reader, IdentifierData data) throws XMLStreamException {
    switch (localName) {
      case LsiouXmlElements.Identifier.NLM_UNIQUE_ID ->
          data.nlmUniqueId = reader.getElementText().trim();
      case LsiouXmlElements.Identifier.NLM_WORK_ID ->
          data.nlmWorkId = reader.getElementText().trim();
      case LsiouXmlElements.Identifier.CODEN -> data.coden = reader.getElementText().trim();
      default -> {
        return false;
      }
    }
    return true;
  }

  // ========== 名称元素处理 ==========

  /// 处理名称元素。
  private boolean handleNameElement(String localName, XMLStreamReader reader, NameData data)
      throws XMLStreamException {
    switch (localName) {
      case LsiouXmlElements.Name.TITLE -> data.title = reader.getElementText().trim();
      case LsiouXmlElements.Name.MEDLINE_TA -> data.medlineTA = reader.getElementText().trim();
      case LsiouXmlElements.Name.SORT_SERIAL_NAME ->
          data.sortSerialName = reader.getElementText().trim();
      default -> {
        return false;
      }
    }
    return true;
  }

  // ========== ISSN 元素处理 ==========

  /// 处理 ISSN 元素。
  private boolean handleIssnElement(String localName, XMLStreamReader reader, IssnData data)
      throws XMLStreamException {
    switch (localName) {
      case LsiouXmlElements.Issn.ISSN_LINKING -> data.issnL = reader.getElementText().trim();
      case LsiouXmlElements.Issn.ISSN -> {
        String issnType = reader.getAttributeValue(null, LsiouXmlElements.Attribute.ISSN_TYPE);
        String issnValue = reader.getElementText().trim();
        if (LsiouXmlElements.Value.ISSN_TYPE_PRINT.equals(issnType)) {
          data.issnPrint = issnValue;
        } else if (LsiouXmlElements.Value.ISSN_TYPE_ELECTRONIC.equals(issnType)) {
          data.issnElectronic = issnValue;
        }
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  // ========== 出版信息元素处理 ==========

  /// 处理出版信息元素。
  private boolean handlePublicationElement(
      String localName, XMLStreamReader reader, SerialParsingState state)
      throws XMLStreamException {
    if (!LsiouXmlElements.Publication.PUBLICATION_INFO.equals(localName)) {
      return false;
    }
    state.publication = parsePublicationInfo(reader);
    return true;
  }

  // ========== 语言元素处理 ==========

  /// 处理语言元素。
  private boolean handleLanguageElement(
      String localName, XMLStreamReader reader, CollectionData data) throws XMLStreamException {
    if (!LsiouXmlElements.Language.LANGUAGE.equals(localName)) {
      return false;
    }
    String langType = reader.getAttributeValue(null, LsiouXmlElements.Attribute.LANG_TYPE);
    String langText = reader.getElementText().trim();
    if (!langText.isEmpty()) {
      boolean isPrimary = "Primary".equalsIgnoreCase(langType);
      data.languages.add(PubmedLanguage.of(langText, isPrimary));
    }
    return true;
  }

  // ========== 索引元素处理 ==========

  /// 处理索引相关元素。
  private boolean handleIndexingElement(String localName, XMLStreamReader reader, IndexingData data)
      throws XMLStreamException {
    switch (localName) {
      case LsiouXmlElements.Indexing.CURRENTLY_INDEXED_YN ->
          data.currentlyIndexedYN = parseYesNo(reader.getElementText());
      case LsiouXmlElements.Indexing.CURRENTLY_INDEXED_FOR_SUBSET -> {
        PubmedCurrentIndexing indexing = parseCurrentlyIndexedForSubset(reader);
        if (indexing != null) {
          data.currentIndexings.add(indexing);
        }
      }
      case LsiouXmlElements.Indexing.INDEXING_SUBSET ->
          data.indexingSubset = reader.getElementText().trim();
      case LsiouXmlElements.Indexing.INDEXING_START_DATE ->
          data.indexingStartDate = reader.getElementText().trim();
      case LsiouXmlElements.Indexing.INDEX_ONLINE_YN ->
          data.indexOnlineYN = parseYesNo(reader.getElementText());
      case LsiouXmlElements.Indexing.INDEXING_SELECTED_URL ->
          data.indexingSelectedURL = reader.getElementText().trim();
      case LsiouXmlElements.Indexing.REPORTED_MEDLINE_YN ->
          data.reportedMedlineYN = parseYesNo(reader.getElementText());
      case LsiouXmlElements.Indexing.PROCESSING_CODE ->
          data.processingCode = reader.getElementText().trim();
      case LsiouXmlElements.Indexing.INDEXING_HISTORY_LIST ->
          data.indexingHistories.addAll(parseIndexingHistoryList(reader));
      default -> {
        return false;
      }
    }
    return true;
  }

  // ========== 分类元素处理 ==========

  /// 处理分类相关元素（MeSH、广泛标题）。
  private boolean handleClassificationElement(
      String localName, XMLStreamReader reader, CollectionData data) throws XMLStreamException {
    switch (localName) {
      case LsiouXmlElements.MeSH.BROAD_JOURNAL_HEADING_LIST ->
          data.broadJournalHeadings.addAll(parseBroadJournalHeadingList(reader));
      case LsiouXmlElements.MeSH.MESH_HEADING_LIST ->
          data.meshHeadings.addAll(parseMeshHeadingList(reader));
      default -> {
        return false;
      }
    }
    return true;
  }

  // ========== 关联元素处理 ==========

  /// 处理关联相关元素（交叉引用、期刊关联、备注）。
  private boolean handleRelationElement(
      String localName, XMLStreamReader reader, CollectionData data) throws XMLStreamException {
    switch (localName) {
      case LsiouXmlElements.Relation.CROSS_REFERENCE_LIST ->
          data.crossReferences.addAll(parseCrossReferenceList(reader));
      case LsiouXmlElements.Relation.TITLE_RELATED -> {
        PubmedTitleRelation relation = parseTitleRelated(reader);
        if (relation != null) {
          data.titleRelations.add(relation);
        }
      }
      case LsiouXmlElements.Relation.GENERAL_NOTE -> {
        PubmedGeneralNote note = parseGeneralNote(reader);
        if (note != null) {
          data.generalNotes.add(note);
        }
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  // ========== 标记元素处理 ==========

  /// 处理标记字段元素。
  private boolean handleMarkerElement(String localName, XMLStreamReader reader, MarkerData data)
      throws XMLStreamException {
    switch (localName) {
      case LsiouXmlElements.Relation.TITLE_CONTINUATION_YN ->
          data.titleContinuationYN = parseYesNo(reader.getElementText());
      case LsiouXmlElements.Relation.MINOR_TITLE_CHANGE_YN ->
          data.minorTitleChangeYN = parseYesNo(reader.getElementText());
      default -> {
        return false;
      }
    }
    return true;
  }

  // ========== 时间戳元素处理 ==========

  /// 处理时间戳元素。
  private void handleTimestampElement(String localName, XMLStreamReader reader, TimestampData data)
      throws XMLStreamException {
    switch (localName) {
      case LsiouXmlElements.Date.ILS_CREATED_TIMESTAMP ->
          data.ilsCreatedTimestamp =
              XmlParsingHelper.parseTimestamp(reader, LsiouXmlElements.Date.ILS_CREATED_TIMESTAMP);
      case LsiouXmlElements.Date.ILS_UPDATED_TIMESTAMP ->
          data.ilsUpdatedTimestamp =
              XmlParsingHelper.parseTimestamp(reader, LsiouXmlElements.Date.ILS_UPDATED_TIMESTAMP);
      case LsiouXmlElements.Date.DELETED_TIMESTAMP ->
          data.deletedTimestamp =
              XmlParsingHelper.parseTimestamp(reader, LsiouXmlElements.Date.DELETED_TIMESTAMP);
      case LsiouXmlElements.Date.MEDLINE_DATA_UPDATED_TIMESTAMP ->
          data.medlineDataUpdatedTimestamp =
              XmlParsingHelper.parseTimestamp(
                  reader, LsiouXmlElements.Date.MEDLINE_DATA_UPDATED_TIMESTAMP);
      case LsiouXmlElements.Date.SEF_CREATED_TIMESTAMP ->
          data.sefCreatedTimestamp =
              XmlParsingHelper.parseTimestamp(reader, LsiouXmlElements.Date.SEF_CREATED_TIMESTAMP);
      case LsiouXmlElements.Date.SEF_UPDATED_TIMESTAMP ->
          data.sefUpdatedTimestamp =
              XmlParsingHelper.parseTimestamp(reader, LsiouXmlElements.Date.SEF_UPDATED_TIMESTAMP);
      default -> {
        // 忽略未处理的元素
      }
    }
  }

  // ========== 结果构建 ==========

  /// 从解析状态构建 PubmedSerialData。
  private PubmedSerialData buildPubmedSerialData(SerialParsingState state) {
    var pub = state.publication;
    return PubmedSerialData.builder()
        // 基本标识符
        .nlmUniqueId(state.identifiers.nlmUniqueId)
        .nlmWorkId(state.identifiers.nlmWorkId)
        .title(state.names.title)
        .medlineTA(state.names.medlineTA)
        .sortSerialName(state.names.sortSerialName)
        // ISSN 和 CODEN
        .issnL(state.issn.issnL)
        .issnPrint(state.issn.issnPrint)
        .issnElectronic(state.issn.issnElectronic)
        .coden(state.identifiers.coden)
        // Serial 属性
        .status(state.attributes.status())
        .pmc(state.attributes.pmc())
        .medPrintYN(state.attributes.medPrintYN())
        .dataCreationMethod(state.attributes.dataCreationMethod())
        // 出版信息
        .country(pub != null ? pub.country() : null)
        .frequency(pub != null ? pub.frequency() : null)
        .frequencyType(pub != null ? pub.frequencyType() : null)
        .publicationFirstYear(pub != null ? pub.firstYear() : null)
        .publicationEndYear(pub != null ? pub.endYear() : null)
        .datesOfSerialPublication(pub != null ? pub.datesOfSerialPublication() : null)
        .places(pub != null ? pub.places() : List.of())
        .publishers(pub != null ? pub.publishers() : List.of())
        // 语言
        .languages(state.collections.languages)
        // 索引相关
        .currentlyIndexedYN(state.indexing.currentlyIndexedYN)
        .currentIndexings(state.indexing.currentIndexings)
        .indexingSubset(state.indexing.indexingSubset)
        .indexingStartDate(state.indexing.indexingStartDate)
        .indexOnlineYN(state.indexing.indexOnlineYN)
        .indexingSelectedURL(state.indexing.indexingSelectedURL)
        .reportedMedlineYN(state.indexing.reportedMedlineYN)
        .processingCode(state.indexing.processingCode)
        // 分类和关联
        .broadJournalHeadings(state.collections.broadJournalHeadings)
        .crossReferences(state.collections.crossReferences)
        .meshHeadings(state.collections.meshHeadings)
        .titleRelations(state.collections.titleRelations)
        .indexingHistories(state.indexing.indexingHistories)
        // 备注
        .generalNotes(state.collections.generalNotes)
        // 标记字段
        .titleContinuationYN(state.markers.titleContinuationYN)
        .minorTitleChangeYN(state.markers.minorTitleChangeYN)
        // 时间戳
        .ilsCreatedTimestamp(state.timestamps.ilsCreatedTimestamp)
        .ilsUpdatedTimestamp(state.timestamps.ilsUpdatedTimestamp)
        .deletedTimestamp(state.timestamps.deletedTimestamp)
        .medlineDataUpdatedTimestamp(state.timestamps.medlineDataUpdatedTimestamp)
        .sefCreatedTimestamp(state.timestamps.sefCreatedTimestamp)
        .sefUpdatedTimestamp(state.timestamps.sefUpdatedTimestamp)
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
