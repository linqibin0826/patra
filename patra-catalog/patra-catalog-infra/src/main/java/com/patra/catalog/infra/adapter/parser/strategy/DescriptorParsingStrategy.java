package com.patra.catalog.infra.adapter.parser.strategy;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.vo.mesh.AllowableQualifier;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import com.patra.catalog.domain.model.vo.mesh.SeeRelatedDescriptor;
import com.patra.catalog.infra.adapter.parser.MeshXmlElements;
import com.patra.catalog.infra.adapter.parser.support.DescriptorListParsers;
import com.patra.catalog.infra.adapter.parser.support.DescriptorListParsers.ConceptListResult;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingHelper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// DescriptorRecord 解析策略。
///
/// 解析 MeSH XML 中的 `<DescriptorRecord>` 元素，创建 `MeshDescriptorAggregate` 聚合根。
///
/// **XML 结构**：
///
/// ```xml
/// <DescriptorRecord DescriptorClass="1">
///   <DescriptorUI>D000001</DescriptorUI>
///   <DescriptorName><String>Calcimycin</String></DescriptorName>
///   <DateCreated>...</DateCreated>
///   <DateRevised>...</DateRevised>
///   <DateEstablished>...</DateEstablished>
///   <HistoryNote>...</HistoryNote>
///   <OnlineNote>...</OnlineNote>
///   <PublicMeSHNote>...</PublicMeSHNote>
///   <NLMClassificationNumber>...</NLMClassificationNumber>
///   <Annotation>...</Annotation>
///   <ConsiderAlso>...</ConsiderAlso>
///   <ScopeNote>...</ScopeNote>
///   <TreeNumberList>...</TreeNumberList>
///   <AllowableQualifiersList>...</AllowableQualifiersList>
///   <PharmacologicalActionList>...</PharmacologicalActionList>
///   <PreviousIndexingList>...</PreviousIndexingList>
///   <SeeRelatedList>...</SeeRelatedList>
///   <EntryCombinationList>...</EntryCombinationList>
///   <ConceptList>...</ConceptList>
/// </DescriptorRecord>
/// ```
///
/// **设计说明**：
///
/// 列表元素的解析逻辑委托给 [DescriptorListParsers] 工具类，
/// 本类只负责：
///
/// 1. 解析 DescriptorClass 属性
/// 2. 解析简单文本/日期字段
/// 3. 分发列表元素到对应解析器
/// 4. 组装最终的聚合根对象
///
/// @author linqibin
/// @since 0.1.0
/// @see DescriptorListParsers
@Slf4j
public final class DescriptorParsingStrategy
    implements RecordParsingStrategy<MeshDescriptorAggregate> {

  /// 单例实例。
  public static final DescriptorParsingStrategy INSTANCE = new DescriptorParsingStrategy();

  private DescriptorParsingStrategy() {}

  @Override
  public String rootElementName() {
    return MeshXmlElements.Record.DESCRIPTOR;
  }

  /// 解析单个 DescriptorRecord 元素。
  ///
  /// **解析流程**：
  ///
  /// 1. 解析 DescriptorClass 属性（默认 TOPICAL）
  /// 2. 遍历子元素，解析基本字段和列表
  /// 3. 验证必填字段（UI 和 Name）
  /// 4. 组装聚合根对象
  ///
  /// @param reader XML 流读取器（已定位到 DescriptorRecord 元素）
  /// @param context 解析上下文（包含 meshVersion）
  /// @return MeshDescriptorAggregate 聚合根，缺少必填字段时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public MeshDescriptorAggregate parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {

    // 1. 解析 DescriptorClass 属性
    DescriptorClass descriptorClass = parseDescriptorClass(reader);

    // 2. 解析子元素
    ParsedFields fields = parseChildElements(reader);

    // 3. 验证必填字段
    if (!fields.isValid()) {
      log.warn(
          "跳过无效 Descriptor（缺少必填字段）: UI={}, Name={}", fields.descriptorUI, fields.descriptorName);
      return null;
    }

    // 4. 组装聚合根（不含版本号，由调用方设置）
    return buildAggregate(fields, descriptorClass);
  }

  // ========== 属性解析 ==========

  /// 解析 DescriptorClass 属性。
  ///
  /// @param reader XML 流读取器
  /// @return DescriptorClass 枚举值，无效时返回 TOPICAL
  private DescriptorClass parseDescriptorClass(XMLStreamReader reader) {
    String classCode = reader.getAttributeValue(null, MeshXmlElements.Attribute.DESCRIPTOR_CLASS);
    if (classCode == null) {
      return DescriptorClass.TOPICAL;
    }

    try {
      return DescriptorClass.fromCode(classCode);
    } catch (IllegalArgumentException e) {
      log.warn("未知的 DescriptorClass 值：{}，使用默认值 TOPICAL", classCode);
      return DescriptorClass.TOPICAL;
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
          && MeshXmlElements.Record.DESCRIPTOR.equals(reader.getLocalName())) {
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
      case MeshXmlElements.Identifier.DESCRIPTOR_UI ->
          fields.descriptorUI = reader.getElementText();
      case MeshXmlElements.Name.DESCRIPTOR_NAME ->
          fields.descriptorName = XmlParsingHelper.parseNameElement(reader);

      // === 日期字段 ===
      case MeshXmlElements.Date.DATE_CREATED ->
          fields.dateCreated =
              XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_CREATED);
      case MeshXmlElements.Date.DATE_REVISED ->
          fields.dateRevised =
              XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_REVISED);
      case MeshXmlElements.Date.DATE_ESTABLISHED ->
          fields.dateEstablished =
              XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_ESTABLISHED);

      // === 文本字段 ===
      case MeshXmlElements.Other.HISTORY_NOTE ->
          fields.historyNote = reader.getElementText().trim();
      case MeshXmlElements.Other.ONLINE_NOTE -> fields.onlineNote = reader.getElementText().trim();
      case MeshXmlElements.Other.PUBLIC_MESH_NOTE ->
          fields.publicMeshNote = reader.getElementText().trim();
      case MeshXmlElements.Other.NLM_CLASSIFICATION_NUMBER ->
          fields.nlmClassificationNumber = reader.getElementText().trim();
      case MeshXmlElements.Other.ANNOTATION -> fields.annotation = reader.getElementText().trim();
      case MeshXmlElements.Other.CONSIDER_ALSO ->
          fields.considerAlso = reader.getElementText().trim();
      case MeshXmlElements.Other.SCOPE_NOTE -> fields.scopeNote = reader.getElementText().trim();

      // === 列表字段（委托给 DescriptorListParsers）===
      case MeshXmlElements.List.TREE_NUMBER_LIST ->
          fields.treeNumbers = DescriptorListParsers.parseTreeNumbers(reader);
      case MeshXmlElements.List.ALLOWABLE_QUALIFIERS_LIST ->
          fields.allowableQualifiers = DescriptorListParsers.parseAllowableQualifiers(reader);
      case MeshXmlElements.List.PHARMACOLOGICAL_ACTION_LIST ->
          fields.pharmacologicalActions = DescriptorListParsers.parsePharmacologicalActions(reader);
      case MeshXmlElements.List.PREVIOUS_INDEXING_LIST ->
          fields.previousIndexings = DescriptorListParsers.parsePreviousIndexings(reader);
      case MeshXmlElements.List.SEE_RELATED_LIST ->
          fields.seeRelatedDescriptors = DescriptorListParsers.parseSeeRelatedDescriptors(reader);
      case MeshXmlElements.List.ENTRY_COMBINATION_LIST ->
          fields.entryCombinations = DescriptorListParsers.parseEntryCombinations(reader);
      case MeshXmlElements.List.CONCEPT_LIST -> {
        ConceptListResult result = DescriptorListParsers.parseConcepts(reader);
        fields.concepts = result.concepts();
        fields.entryTerms = result.entryTerms();
      }

      default -> {
        // 跳过其他未处理的元素（如 RecordOriginatorsList 等）
      }
    }
  }

  // ========== 聚合根组装 ==========

  /// 构建聚合根对象（不含版本号）。
  ///
  /// 返回的聚合根不包含 meshVersion，由调用方通过 `withMeshVersion()` 设置。
  ///
  /// @param fields 已解析的字段
  /// @param descriptorClass 主题词分类
  /// @return 聚合根对象
  private MeshDescriptorAggregate buildAggregate(
      ParsedFields fields, DescriptorClass descriptorClass) {
    // 创建聚合根（不含版本号）
    MeshDescriptorAggregate aggregate =
        MeshDescriptorAggregate.create(
            MeshUI.of(fields.descriptorUI), fields.descriptorName, descriptorClass);

    // 设置日期字段
    setDateFields(aggregate, fields);

    // 设置文本字段
    setTextFields(aggregate, fields);

    // 添加集合数据
    addCollections(aggregate, fields);

    return aggregate;
  }

  /// 设置日期字段。
  private void setDateFields(MeshDescriptorAggregate aggregate, ParsedFields fields) {
    if (fields.dateCreated != null) {
      aggregate.setDateCreated(fields.dateCreated);
    }
    if (fields.dateRevised != null) {
      aggregate.setDateRevised(fields.dateRevised);
    }
    if (fields.dateEstablished != null) {
      aggregate.setDateEstablished(fields.dateEstablished);
    }
  }

  /// 设置文本字段。
  private void setTextFields(MeshDescriptorAggregate aggregate, ParsedFields fields) {
    if (fields.historyNote != null) {
      aggregate.setHistoryNote(fields.historyNote);
    }
    if (fields.onlineNote != null) {
      aggregate.setOnlineNote(fields.onlineNote);
    }
    if (fields.publicMeshNote != null) {
      aggregate.setPublicMeshNote(fields.publicMeshNote);
    }
    if (fields.nlmClassificationNumber != null) {
      aggregate.setNlmClassificationNumber(fields.nlmClassificationNumber);
    }
    if (fields.annotation != null) {
      aggregate.setAnnotation(fields.annotation);
    }
    if (fields.considerAlso != null) {
      aggregate.setConsiderAlso(fields.considerAlso);
    }
    if (fields.scopeNote != null) {
      aggregate.setScopeNote(fields.scopeNote);
    }
  }

  /// 添加集合数据。
  private void addCollections(MeshDescriptorAggregate aggregate, ParsedFields fields) {
    if (!fields.treeNumbers.isEmpty()) {
      aggregate.addTreeNumbers(fields.treeNumbers);
    }
    if (!fields.allowableQualifiers.isEmpty()) {
      aggregate.addAllowableQualifiers(fields.allowableQualifiers);
    }
    if (!fields.pharmacologicalActions.isEmpty()) {
      aggregate.addPharmacologicalActions(fields.pharmacologicalActions);
    }
    if (!fields.previousIndexings.isEmpty()) {
      aggregate.addPreviousIndexings(fields.previousIndexings);
    }
    if (!fields.seeRelatedDescriptors.isEmpty()) {
      aggregate.addSeeRelatedDescriptors(fields.seeRelatedDescriptors);
    }
    if (!fields.concepts.isEmpty()) {
      aggregate.addConcepts(fields.concepts);
    }
    if (!fields.entryTerms.isEmpty()) {
      aggregate.addEntryTerms(fields.entryTerms);
    }
    if (!fields.entryCombinations.isEmpty()) {
      aggregate.addEntryCombinations(fields.entryCombinations);
    }
  }

  // ========== 内部类 ==========

  /// 解析字段容器。
  ///
  /// 用于在解析过程中临时存储各字段值，避免方法参数过多。
  private static class ParsedFields {

    // 标识符和名称
    String descriptorUI;
    String descriptorName;

    // 日期字段
    LocalDate dateCreated;
    LocalDate dateRevised;
    LocalDate dateEstablished;

    // 文本字段
    String historyNote;
    String onlineNote;
    String publicMeshNote;
    String nlmClassificationNumber;
    String annotation;
    String considerAlso;
    String scopeNote;

    // 集合字段
    List<MeshTreeNumber> treeNumbers = new ArrayList<>();
    List<AllowableQualifier> allowableQualifiers = new ArrayList<>();
    List<PharmacologicalAction> pharmacologicalActions = new ArrayList<>();
    List<String> previousIndexings = new ArrayList<>();
    List<SeeRelatedDescriptor> seeRelatedDescriptors = new ArrayList<>();
    List<MeshConcept> concepts = new ArrayList<>();
    List<MeshEntryTerm> entryTerms = new ArrayList<>();
    List<EntryCombination> entryCombinations = new ArrayList<>();

    /// 检查必填字段是否有效。
    boolean isValid() {
      return descriptorUI != null && descriptorName != null;
    }
  }
}
