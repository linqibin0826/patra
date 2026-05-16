package dev.linqibin.patra.catalog.infra.parser.strategy;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import dev.linqibin.patra.catalog.domain.model.entity.MeshConcept;
import dev.linqibin.patra.catalog.domain.model.entity.MeshEntryTerm;
import dev.linqibin.patra.catalog.domain.model.enums.ScrClass;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.HeadingMappedTo;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.IndexingInfo;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshUI;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.ScrSource;
import dev.linqibin.patra.catalog.infra.parser.MeshXmlElements;
import dev.linqibin.patra.catalog.infra.parser.support.MeshListParsers;
import dev.linqibin.patra.catalog.infra.parser.support.MeshListParsers.ConceptListResult;
import dev.linqibin.patra.catalog.infra.parser.support.XmlParsingContext;
import dev.linqibin.patra.catalog.infra.parser.support.XmlParsingHelper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// SupplementalRecord (SCR) 解析策略。
///
/// 解析 MeSH XML 中的 `<SupplementalRecord>` 元素，创建 `MeshScrAggregate` 聚合根。
///
/// **XML 结构**：
///
/// ```xml
/// <SupplementalRecord SCRClass="1">
///   <SupplementalRecordUI>C000001</SupplementalRecordUI>
///   <SupplementalRecordName><String>Name</String></SupplementalRecordName>
///   <DateCreated>...</DateCreated>
///   <DateRevised>...</DateRevised>
///   <Note>...</Note>
///   <Frequency>...</Frequency>
///   <PreviousIndexingList>...</PreviousIndexingList>
///   <HeadingMappedToList>...</HeadingMappedToList>
///   <IndexingInformationList>...</IndexingInformationList>
///   <PharmacologicalActionList>...</PharmacologicalActionList>
///   <SourceList>...</SourceList>
///   <ConceptList>...</ConceptList>
/// </SupplementalRecord>
/// ```
///
/// **SCR 类别（SCRClass）**：
///
/// - 1: 化学物质和药物（CHEMICAL）
/// - 2: 化疗方案（PROTOCOL）
/// - 3: 疾病（DISEASE）
/// - 4: 生物体（ORGANISM，2018年新增）
/// - 5: 人群组（POPULATION_GROUP，2023年新增）
/// - 6: 解剖结构（ANATOMY）
///
/// **与 Descriptor 的区别**：
///
/// - SCR 没有 TreeNumberList（无树形结构）
/// - SCR 通过 HeadingMappedToList 映射到 Descriptor
/// - SCR 有 SourceList 和 IndexingInformationList
///
/// @author linqibin
/// @since 0.1.0
/// @see MeshListParsers
@Slf4j
public final class ScrParsingStrategy implements RecordParsingStrategy<MeshScrAggregate> {

  /// 单例实例。
  public static final ScrParsingStrategy INSTANCE = new ScrParsingStrategy();

  private ScrParsingStrategy() {}

  @Override
  public String rootElementName() {
    return MeshXmlElements.Record.SUPPLEMENTAL;
  }

  /// 解析单个 SupplementalRecord 元素。
  ///
  /// **解析流程**：
  ///
  /// 1. 解析 SCRClass 属性（默认 CHEMICAL）
  /// 2. 遍历子元素，解析基本字段和列表
  /// 3. 验证必填字段（UI 和 Name）
  /// 4. 组装聚合根对象
  ///
  /// @param reader XML 流读取器（已定位到 SupplementalRecord 元素）
  /// @param context 解析上下文（包含 meshVersion）
  /// @return MeshScrAggregate 聚合根，缺少必填字段时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public MeshScrAggregate parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {

    // 1. 解析 SCRClass 属性
    ScrClass scrClass = parseScrClass(reader);

    // 2. 解析子元素
    ParsedFields fields = parseChildElements(reader);

    // 3. 验证必填字段
    if (!fields.isValid()) {
      log.warn("跳过无效 SCR（缺少必填字段）: UI={}, Name={}", fields.scrUi, fields.scrName);
      return null;
    }

    // 4. 组装聚合根（不含版本号，由调用方设置）
    return buildAggregate(fields, scrClass);
  }

  // ========== 属性解析 ==========

  /// 解析 SCRClass 属性。
  ///
  /// @param reader XML 流读取器
  /// @return ScrClass 枚举值，无效时返回 CHEMICAL
  private ScrClass parseScrClass(XMLStreamReader reader) {
    String classCode = reader.getAttributeValue(null, MeshXmlElements.Attribute.SCR_CLASS);
    if (classCode == null) {
      return ScrClass.CHEMICAL;
    }

    try {
      int code = Integer.parseInt(classCode);
      return ScrClass.fromCode(code);
    } catch (IllegalArgumentException e) {
      log.warn("未知的 SCRClass 值：{}，使用默认值 CHEMICAL", classCode);
      return ScrClass.CHEMICAL;
    }
  }

  // ========== 子元素解析 ==========

  /// 解析所有子元素。
  ///
  /// @param reader XML 流读取器
  /// @return 解析后的字段容器
  /// @throws XMLStreamException XML 解析异常
  private ParsedFields parseChildElements(XMLStreamReader reader) throws XMLStreamException {
    ParsedFields fields = new ParsedFields();

    while (reader.hasNext()) {
      int event = reader.next();

      if (event == XMLStreamConstants.START_ELEMENT) {
        parseStartElement(reader, fields);
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Record.SUPPLEMENTAL.equals(reader.getLocalName())) {
        break;
      }
    }

    return fields;
  }

  /// 解析单个起始元素。
  ///
  /// 根据元素名称分发到对应的解析逻辑。
  ///
  /// @param reader XML 流读取器
  /// @param fields 字段容器（用于存储解析结果）
  /// @throws XMLStreamException XML 解析异常
  private void parseStartElement(XMLStreamReader reader, ParsedFields fields)
      throws XMLStreamException {
    String localName = reader.getLocalName();

    switch (localName) {
      // === 标识符和名称 ===
      case MeshXmlElements.Identifier.SUPPLEMENTAL_RECORD_UI ->
          fields.scrUi = reader.getElementText();
      case MeshXmlElements.Name.SUPPLEMENTAL_RECORD_NAME ->
          fields.scrName = XmlParsingHelper.parseNameElement(reader);

      // === 日期字段 ===
      case MeshXmlElements.Date.DATE_CREATED ->
          fields.dateCreated =
              XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_CREATED);
      case MeshXmlElements.Date.DATE_REVISED ->
          fields.dateRevised =
              XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_REVISED);

      // === 文本字段 ===
      case MeshXmlElements.Other.NOTE -> fields.note = reader.getElementText().trim();
      case MeshXmlElements.Other.FREQUENCY -> fields.frequency = reader.getElementText().trim();

      // === 列表字段（委托给 MeshListParsers）===
      case MeshXmlElements.List.PREVIOUS_INDEXING_LIST ->
          fields.previousIndexings = MeshListParsers.parsePreviousIndexings(reader);
      case MeshXmlElements.List.HEADING_MAPPED_TO_LIST ->
          fields.headingMappedTos = MeshListParsers.parseHeadingMappedTos(reader);
      case MeshXmlElements.List.INDEXING_INFORMATION_LIST ->
          fields.indexingInfos = MeshListParsers.parseIndexingInfos(reader);
      case MeshXmlElements.List.PHARMACOLOGICAL_ACTION_LIST ->
          fields.pharmacologicalActions = MeshListParsers.parsePharmacologicalActions(reader);
      case MeshXmlElements.List.SOURCE_LIST ->
          fields.sources = MeshListParsers.parseSources(reader);
      case MeshXmlElements.List.CONCEPT_LIST -> {
        ConceptListResult result = MeshListParsers.parseConcepts(reader);
        fields.concepts = result.concepts();
        fields.entryTerms = result.entryTerms();
      }

      default -> {
        // 跳过其他未处理的元素
      }
    }
  }

  // ========== 聚合根组装 ==========

  /// 构建聚合根对象（不含版本号）。
  ///
  /// 返回的聚合根不包含 meshVersion，由调用方通过 `withMeshVersion()` 设置。
  ///
  /// @param fields 已解析的字段
  /// @param scrClass SCR 类别
  /// @return 聚合根对象
  private MeshScrAggregate buildAggregate(ParsedFields fields, ScrClass scrClass) {
    // 创建聚合根（不含版本号）
    MeshScrAggregate aggregate =
        MeshScrAggregate.create(MeshUI.of(fields.scrUi), fields.scrName, scrClass);

    // 设置日期字段
    setDateFields(aggregate, fields);

    // 设置文本字段
    setTextFields(aggregate, fields);

    // 添加集合数据
    addCollections(aggregate, fields);

    return aggregate;
  }

  /// 设置日期字段。
  private void setDateFields(MeshScrAggregate aggregate, ParsedFields fields) {
    if (fields.dateCreated != null) {
      aggregate.withDateCreated(fields.dateCreated);
    }
    if (fields.dateRevised != null) {
      aggregate.withDateRevised(fields.dateRevised);
    }
  }

  /// 设置文本字段。
  private void setTextFields(MeshScrAggregate aggregate, ParsedFields fields) {
    if (fields.note != null) {
      aggregate.withNote(fields.note);
    }
    if (fields.frequency != null) {
      aggregate.withFrequency(fields.frequency);
    }
    if (!fields.previousIndexings.isEmpty()) {
      // 将 PreviousIndexing 列表合并为单个字符串（用换行符分隔）
      aggregate.withPreviousIndexing(String.join("\n", fields.previousIndexings));
    }
  }

  /// 添加集合数据。
  private void addCollections(MeshScrAggregate aggregate, ParsedFields fields) {
    if (!fields.headingMappedTos.isEmpty()) {
      aggregate.addHeadingMappedTos(fields.headingMappedTos);
    }
    if (!fields.pharmacologicalActions.isEmpty()) {
      aggregate.addPharmacologicalActions(fields.pharmacologicalActions);
    }
    if (!fields.sources.isEmpty()) {
      aggregate.addSources(fields.sources);
    }
    if (!fields.indexingInfos.isEmpty()) {
      aggregate.addIndexingInfos(fields.indexingInfos);
    }
    if (!fields.concepts.isEmpty()) {
      aggregate.addConcepts(fields.concepts);
    }
    if (!fields.entryTerms.isEmpty()) {
      aggregate.addEntryTerms(fields.entryTerms);
    }
  }

  // ========== 内部类 ==========

  /// 解析字段容器。
  ///
  /// 用于在解析过程中临时存储各字段值，避免方法参数过多。
  private static class ParsedFields {

    // 标识符和名称
    String scrUi;
    String scrName;

    // 日期字段
    LocalDate dateCreated;
    LocalDate dateRevised;

    // 文本字段
    String note;
    String frequency;

    // 集合字段
    List<String> previousIndexings = new ArrayList<>();
    List<HeadingMappedTo> headingMappedTos = new ArrayList<>();
    List<PharmacologicalAction> pharmacologicalActions = new ArrayList<>();
    List<ScrSource> sources = new ArrayList<>();
    List<IndexingInfo> indexingInfos = new ArrayList<>();
    List<MeshConcept> concepts = new ArrayList<>();
    List<MeshEntryTerm> entryTerms = new ArrayList<>();

    /// 检查必填字段是否有效。
    boolean isValid() {
      return scrUi != null && scrName != null;
    }
  }
}
