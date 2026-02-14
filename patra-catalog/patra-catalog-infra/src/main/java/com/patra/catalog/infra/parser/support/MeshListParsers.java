package com.patra.catalog.infra.parser.support;

import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.vo.mesh.AllowableQualifier;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.HeadingMappedTo;
import com.patra.catalog.domain.model.vo.mesh.IndexingInfo;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import com.patra.catalog.domain.model.vo.mesh.ScrSource;
import com.patra.catalog.domain.model.vo.mesh.SeeRelatedDescriptor;
import com.patra.catalog.infra.parser.MeshXmlElements;
import com.patra.catalog.infra.parser.strategy.EntryTermParsingStrategy;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// MeSH 列表元素解析工具类。
///
/// 提供 MeSH Descriptor 和 SCR 记录中各种列表元素的解析方法，
/// 支持 Descriptor 和 SCR 共用的解析逻辑（如 ConceptList、PharmacologicalActionList）。
///
/// **设计原则**：
///
/// - 所有方法为静态方法，无状态，线程安全
/// - 每个方法负责解析一种列表类型
/// - 方法命名遵循 `parse{ListName}` 模式
/// - 异常处理：解析失败记录警告日志，返回空结果，不中断整体流程
///
/// **支持的列表类型**：
///
/// | 列表元素 | 方法名 | 适用范围 |
/// |---------|--------|---------|
/// | TreeNumberList | parseTreeNumbers | Descriptor 专用 |
/// | AllowableQualifiersList | parseAllowableQualifiers | Descriptor 专用 |
/// | PharmacologicalActionList | parsePharmacologicalActions | Descriptor/SCR 共用 |
/// | PreviousIndexingList | parsePreviousIndexings | Descriptor/SCR 共用 |
/// | SeeRelatedList | parseSeeRelatedDescriptors | Descriptor 专用 |
/// | EntryCombinationList | parseEntryCombinations | Descriptor 专用 |
/// | ConceptList | parseConcepts | Descriptor/SCR 共用 |
/// | HeadingMappedToList | parseHeadingMappedTos | SCR 专用 |
/// | SourceList | parseSources | SCR 专用 |
/// | IndexingInformationList | parseIndexingInfos | SCR 专用 |
///
/// @author linqibin
/// @since 0.1.0
/// @see com.patra.catalog.infra.parser.strategy.DescriptorParsingStrategy
/// @see com.patra.catalog.infra.parser.strategy.ScrParsingStrategy
@Slf4j
public final class MeshListParsers {

  private MeshListParsers() {
    throw new UnsupportedOperationException("工具类禁止实例化");
  }

  // ========== TreeNumberList ==========

  /// 解析 TreeNumberList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <TreeNumberList>
  ///   <TreeNumber>A01.001</TreeNumber>
  ///   <TreeNumber>B02.002</TreeNumber>
  /// </TreeNumberList>
  /// ```
  ///
  /// **业务规则**：第一个 TreeNumber 标记为 isPrimary=true。
  ///
  /// @param reader XML 流读取器（已定位到 TreeNumberList）
  /// @return 树形编号列表
  /// @throws XMLStreamException XML 解析异常
  public static List<MeshTreeNumber> parseTreeNumbers(XMLStreamReader reader)
      throws XMLStreamException {
    List<MeshTreeNumber> treeNumbers = new ArrayList<>();
    int index = 0;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Record.TREE_NUMBER.equals(reader.getLocalName())) {
        String treeNumber = reader.getElementText();
        // 第一个标记为主要树形编号
        treeNumbers.add(MeshTreeNumber.create(treeNumber, index == 0));
        index++;
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.TREE_NUMBER_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return treeNumbers;
  }

  // ========== AllowableQualifiersList ==========

  /// 解析 AllowableQualifiersList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <AllowableQualifiersList>
  ///   <AllowableQualifier>
  ///     <QualifierReferredTo>
  ///       <QualifierUI>Q000001</QualifierUI>
  ///       <QualifierName><String>diagnosis</String></QualifierName>
  ///     </QualifierReferredTo>
  ///     <Abbreviation>DI</Abbreviation>
  ///   </AllowableQualifier>
  /// </AllowableQualifiersList>
  /// ```
  ///
  /// @param reader XML 流读取器（已定位到 AllowableQualifiersList）
  /// @return 允许限定词列表
  /// @throws XMLStreamException XML 解析异常
  public static List<AllowableQualifier> parseAllowableQualifiers(XMLStreamReader reader)
      throws XMLStreamException {
    List<AllowableQualifier> qualifiers = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.ALLOWABLE_QUALIFIER.equals(reader.getLocalName())) {
        AllowableQualifier qualifier = parseSingleAllowableQualifier(reader);
        if (qualifier != null) {
          qualifiers.add(qualifier);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.ALLOWABLE_QUALIFIERS_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return qualifiers;
  }

  /// 解析单个 AllowableQualifier 元素。
  private static AllowableQualifier parseSingleAllowableQualifier(XMLStreamReader reader)
      throws XMLStreamException {
    ReferredTo qualifierRef = ReferredTo.empty();
    String abbreviation = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (MeshXmlElements.Referred.QUALIFIER_REFERRED_TO.equals(localName)) {
          qualifierRef = ReferredTo.parseQualifier(reader);
        } else if (MeshXmlElements.Other.ABBREVIATION.equals(localName)) {
          abbreviation = reader.getElementText();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.ALLOWABLE_QUALIFIER.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段
    if (qualifierRef.isValid() && abbreviation != null) {
      try {
        return AllowableQualifier.of(qualifierRef.toMeshUI(), qualifierRef.name(), abbreviation);
      } catch (Exception e) {
        log.warn("解析 AllowableQualifier 失败: {}", qualifierRef, e);
      }
    }

    return null;
  }

  // ========== PharmacologicalActionList ==========

  /// 解析 PharmacologicalActionList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <PharmacologicalActionList>
  ///   <PharmacologicalAction>
  ///     <DescriptorReferredTo>
  ///       <DescriptorUI>D000001</DescriptorUI>
  ///       <DescriptorName><String>Calcimycin</String></DescriptorName>
  ///     </DescriptorReferredTo>
  ///   </PharmacologicalAction>
  /// </PharmacologicalActionList>
  /// ```
  ///
  /// @param reader XML 流读取器（已定位到 PharmacologicalActionList）
  /// @return 药理作用列表
  /// @throws XMLStreamException XML 解析异常
  public static List<PharmacologicalAction> parsePharmacologicalActions(XMLStreamReader reader)
      throws XMLStreamException {
    List<PharmacologicalAction> actions = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.PHARMACOLOGICAL_ACTION.equals(reader.getLocalName())) {
        PharmacologicalAction action = parseSinglePharmacologicalAction(reader);
        if (action != null) {
          actions.add(action);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.PHARMACOLOGICAL_ACTION_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return actions;
  }

  /// 解析单个 PharmacologicalAction 元素。
  private static PharmacologicalAction parseSinglePharmacologicalAction(XMLStreamReader reader)
      throws XMLStreamException {
    ReferredTo descriptorRef = ReferredTo.empty();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO.equals(reader.getLocalName())) {
        descriptorRef = ReferredTo.parseDescriptor(reader);
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.PHARMACOLOGICAL_ACTION.equals(reader.getLocalName())) {
        break;
      }
    }

    if (descriptorRef.isValid()) {
      try {
        return PharmacologicalAction.of(descriptorRef.toMeshUI(), descriptorRef.name());
      } catch (Exception e) {
        log.warn("解析 PharmacologicalAction 失败: {}", descriptorRef, e);
      }
    }

    return null;
  }

  // ========== PreviousIndexingList ==========

  /// 解析 PreviousIndexingList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <PreviousIndexingList>
  ///   <PreviousIndexing>Calcimycin (1973-1977)</PreviousIndexing>
  /// </PreviousIndexingList>
  /// ```
  ///
  /// @param reader XML 流读取器（已定位到 PreviousIndexingList）
  /// @return 历史索引列表
  /// @throws XMLStreamException XML 解析异常
  public static List<String> parsePreviousIndexings(XMLStreamReader reader)
      throws XMLStreamException {
    return XmlParsingHelper.parseStringList(
        reader,
        MeshXmlElements.List.PREVIOUS_INDEXING_LIST,
        MeshXmlElements.Other.PREVIOUS_INDEXING);
  }

  // ========== SeeRelatedList ==========

  /// 解析 SeeRelatedList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <SeeRelatedList>
  ///   <SeeRelatedDescriptor>
  ///     <DescriptorReferredTo>
  ///       <DescriptorUI>D000002</DescriptorUI>
  ///       <DescriptorName><String>Related Term</String></DescriptorName>
  ///     </DescriptorReferredTo>
  ///   </SeeRelatedDescriptor>
  /// </SeeRelatedList>
  /// ```
  ///
  /// @param reader XML 流读取器（已定位到 SeeRelatedList）
  /// @return 相关主题词列表
  /// @throws XMLStreamException XML 解析异常
  public static List<SeeRelatedDescriptor> parseSeeRelatedDescriptors(XMLStreamReader reader)
      throws XMLStreamException {
    List<SeeRelatedDescriptor> descriptors = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.SEE_RELATED_DESCRIPTOR.equals(reader.getLocalName())) {
        SeeRelatedDescriptor descriptor = parseSingleSeeRelatedDescriptor(reader);
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.SEE_RELATED_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return descriptors;
  }

  /// 解析单个 SeeRelatedDescriptor 元素。
  private static SeeRelatedDescriptor parseSingleSeeRelatedDescriptor(XMLStreamReader reader)
      throws XMLStreamException {
    ReferredTo descriptorRef = ReferredTo.empty();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO.equals(reader.getLocalName())) {
        descriptorRef = ReferredTo.parseDescriptor(reader);
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.SEE_RELATED_DESCRIPTOR.equals(reader.getLocalName())) {
        break;
      }
    }

    if (descriptorRef.isValid()) {
      try {
        return SeeRelatedDescriptor.of(descriptorRef.toMeshUI(), descriptorRef.name());
      } catch (Exception e) {
        log.warn("解析 SeeRelatedDescriptor 失败: {}", descriptorRef, e);
      }
    }

    return null;
  }

  // ========== EntryCombinationList ==========

  /// ECIN/ECOUT 解析结果。
  ///
  /// 封装 EntryCombination 的输入输出引用。
  ///
  /// @param descriptorUi 主题词 UI
  /// @param qualifierUi 限定词 UI（可为空）
  private record EcReference(String descriptorUi, String qualifierUi) {

    boolean hasDescriptor() {
      return descriptorUi != null;
    }
  }

  /// 解析 EntryCombinationList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <EntryCombinationList>
  ///   <EntryCombination>
  ///     <ECIN>
  ///       <DescriptorReferredTo>...</DescriptorReferredTo>
  ///       <QualifierReferredTo>...</QualifierReferredTo>
  ///     </ECIN>
  ///     <ECOUT>
  ///       <DescriptorReferredTo>...</DescriptorReferredTo>
  ///       <QualifierReferredTo>...</QualifierReferredTo>  <!-- 可选 -->
  ///     </ECOUT>
  ///   </EntryCombination>
  /// </EntryCombinationList>
  /// ```
  ///
  /// @param reader XML 流读取器（已定位到 EntryCombinationList）
  /// @return 组合条目列表
  /// @throws XMLStreamException XML 解析异常
  public static List<EntryCombination> parseEntryCombinations(XMLStreamReader reader)
      throws XMLStreamException {
    List<EntryCombination> combinations = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.ENTRY_COMBINATION.equals(reader.getLocalName())) {
        EntryCombination combination = parseSingleEntryCombination(reader);
        if (combination != null) {
          combinations.add(combination);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.ENTRY_COMBINATION_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return combinations;
  }

  /// 解析单个 EntryCombination 元素。
  private static EntryCombination parseSingleEntryCombination(XMLStreamReader reader)
      throws XMLStreamException {
    EcReference ecin = new EcReference(null, null);
    EcReference ecout = new EcReference(null, null);

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (MeshXmlElements.Referred.ECIN.equals(localName)) {
          ecin = parseEcReference(reader, MeshXmlElements.Referred.ECIN);
        } else if (MeshXmlElements.Referred.ECOUT.equals(localName)) {
          ecout = parseEcReference(reader, MeshXmlElements.Referred.ECOUT);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.ENTRY_COMBINATION.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段：ECIN 和 ECOUT 的 Descriptor 必须存在
    if (ecin.hasDescriptor() && ecin.qualifierUi() != null && ecout.hasDescriptor()) {
      try {
        return EntryCombination.of(
            MeshUI.of(ecin.descriptorUi()),
            MeshUI.of(ecin.qualifierUi()),
            MeshUI.of(ecout.descriptorUi()),
            ecout.qualifierUi() != null ? MeshUI.of(ecout.qualifierUi()) : null);
      } catch (Exception e) {
        log.warn("解析 EntryCombination 失败", e);
      }
    }

    return null;
  }

  /// 解析 ECIN 或 ECOUT 元素。
  private static EcReference parseEcReference(XMLStreamReader reader, String elementName)
      throws XMLStreamException {
    String descriptorUi = null;
    String qualifierUi = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO.equals(localName)) {
          ReferredTo ref = ReferredTo.parseDescriptor(reader);
          descriptorUi = ref.ui();
        } else if (MeshXmlElements.Referred.QUALIFIER_REFERRED_TO.equals(localName)) {
          ReferredTo ref = ReferredTo.parseQualifier(reader);
          qualifierUi = ref.ui();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && elementName.equals(reader.getLocalName())) {
        break;
      }
    }

    return new EcReference(descriptorUi, qualifierUi);
  }

  // ========== ConceptList ==========

  /// ConceptList 解析结果。
  ///
  /// 包含 Concept 列表和从 TermList 提取的 EntryTerm 列表。
  ///
  /// @param concepts 概念列表
  /// @param entryTerms 入口术语列表（从各 Concept 的 TermList 提取）
  public record ConceptListResult(List<MeshConcept> concepts, List<MeshEntryTerm> entryTerms) {

    /// 创建空结果。
    public static ConceptListResult empty() {
      return new ConceptListResult(new ArrayList<>(), new ArrayList<>());
    }
  }

  /// 解析 ConceptList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <ConceptList>
  ///   <Concept PreferredConceptYN="Y">
  ///     <ConceptUI>M0000001</ConceptUI>
  ///     <ConceptName><String>Concept Name</String></ConceptName>
  ///     <ScopeNote>...</ScopeNote>
  ///     <TermList>
  ///       <Term>...</Term>
  ///     </TermList>
  ///   </Concept>
  /// </ConceptList>
  /// ```
  ///
  /// **设计说明**：
  ///
  /// 同时提取 Concept 和 EntryTerm，因为 EntryTerm 嵌套在 Concept 的 TermList 中，
  /// 但在领域模型中作为 Descriptor 的直接子实体存储。
  ///
  /// @param reader XML 流读取器（已定位到 ConceptList）
  /// @return 解析结果（包含 Concept 和 EntryTerm 列表）
  /// @throws XMLStreamException XML 解析异常
  public static ConceptListResult parseConcepts(XMLStreamReader reader) throws XMLStreamException {
    List<MeshConcept> concepts = new ArrayList<>();
    List<MeshEntryTerm> entryTerms = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Record.CONCEPT.equals(reader.getLocalName())) {
        parseSingleConcept(reader, concepts, entryTerms);
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.CONCEPT_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return new ConceptListResult(concepts, entryTerms);
  }

  /// 解析单个 Concept 元素及其 TermList。
  private static void parseSingleConcept(
      XMLStreamReader reader, List<MeshConcept> concepts, List<MeshEntryTerm> entryTerms)
      throws XMLStreamException {
    // 解析 PreferredConceptYN 属性
    boolean isPreferred =
        XmlParsingHelper.parseYesNoAttribute(
            reader, MeshXmlElements.Attribute.PREFERRED_CONCEPT_YN, false);

    // Concept 字段
    String conceptUi = null;
    String conceptName = null;
    String scopeNote = null;
    String casn1Name = null;
    String conceptStatus = null;
    String translatorsEnglishScopeNote = null;
    String translatorsScopeNote = null;
    List<String> registryNumbers = new ArrayList<>();
    List<String> relatedRegistryNumbers = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Identifier.CONCEPT_UI -> conceptUi = reader.getElementText();
          case MeshXmlElements.Name.CONCEPT_NAME ->
              conceptName = XmlParsingHelper.parseNameElement(reader);
          case MeshXmlElements.Other.SCOPE_NOTE -> scopeNote = reader.getElementText();
          case MeshXmlElements.Other.CASN1_NAME -> casn1Name = reader.getElementText();
          case MeshXmlElements.Other.CONCEPT_STATUS -> conceptStatus = reader.getElementText();
          case MeshXmlElements.Other.TRANSLATORS_ENGLISH_SCOPE_NOTE ->
              translatorsEnglishScopeNote = reader.getElementText();
          case MeshXmlElements.Other.TRANSLATORS_SCOPE_NOTE ->
              translatorsScopeNote = reader.getElementText();
          case MeshXmlElements.Other.REGISTRY_NUMBER -> {
            String regNum = reader.getElementText();
            if (regNum != null && !regNum.isBlank()) {
              registryNumbers.add(regNum.trim());
            }
          }
          case MeshXmlElements.List.REGISTRY_NUMBER_LIST ->
              registryNumbers.addAll(parseRegistryNumbers(reader));
          case MeshXmlElements.List.RELATED_REGISTRY_NUMBER_LIST ->
              relatedRegistryNumbers.addAll(parseRelatedRegistryNumbers(reader));
          case MeshXmlElements.List.TERM_LIST -> parseTermList(reader, entryTerms);
          default -> {
            // 跳过其他未处理的元素
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Record.CONCEPT.equals(reader.getLocalName())) {
        break;
      }
    }

    // 创建 Concept
    if (conceptUi != null && conceptName != null) {
      try {
        MeshConcept concept = MeshConcept.create(MeshUI.of(conceptUi), conceptName, isPreferred);

        if (!registryNumbers.isEmpty()) {
          concept.addRegistryNumbers(registryNumbers);
        }
        if (scopeNote != null) {
          concept.withScopeNote(scopeNote);
        }
        if (casn1Name != null) {
          concept.withCasn1Name(casn1Name);
        }
        if (conceptStatus != null) {
          concept.withConceptStatus(conceptStatus);
        }
        if (translatorsEnglishScopeNote != null) {
          concept.withTranslatorsEnglishScopeNote(translatorsEnglishScopeNote);
        }
        if (translatorsScopeNote != null) {
          concept.withTranslatorsScopeNote(translatorsScopeNote);
        }
        if (!relatedRegistryNumbers.isEmpty()) {
          concept.addRelatedRegistryNumbers(relatedRegistryNumbers);
        }

        concepts.add(concept);
      } catch (Exception e) {
        log.warn("解析 Concept 失败: UI={}, Name={}", conceptUi, conceptName, e);
      }
    }
  }

  /// 解析 RegistryNumberList。
  private static List<String> parseRegistryNumbers(XMLStreamReader reader)
      throws XMLStreamException {
    return XmlParsingHelper.parseStringList(
        reader, MeshXmlElements.List.REGISTRY_NUMBER_LIST, MeshXmlElements.Other.REGISTRY_NUMBER);
  }

  /// 解析 RelatedRegistryNumberList。
  private static List<String> parseRelatedRegistryNumbers(XMLStreamReader reader)
      throws XMLStreamException {
    return XmlParsingHelper.parseStringList(
        reader,
        MeshXmlElements.List.RELATED_REGISTRY_NUMBER_LIST,
        MeshXmlElements.Other.RELATED_REGISTRY_NUMBER);
  }

  /// 解析 TermList 并添加到 entryTerms 列表。
  private static void parseTermList(XMLStreamReader reader, List<MeshEntryTerm> entryTerms)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Record.TERM.equals(reader.getLocalName())) {
        // 复用 EntryTermParsingStrategy
        MeshEntryTerm term =
            EntryTermParsingStrategy.INSTANCE.parseRecord(reader, XmlParsingContext.empty());
        if (term != null) {
          entryTerms.add(term);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.TERM_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  // ========== SCR 特有列表解析方法 ==========

  /// 解析 HeadingMappedToList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <HeadingMappedToList>
  ///   <HeadingMappedTo>
  ///     <DescriptorReferredTo>
  ///       <DescriptorUI>D000001</DescriptorUI>
  ///       <DescriptorName><String>Calcimycin</String></DescriptorName>
  ///     </DescriptorReferredTo>
  ///     <QualifierReferredTo>  <!-- 可选 -->
  ///       <QualifierUI>Q000627</QualifierUI>
  ///       <QualifierName><String>therapeutic use</String></QualifierName>
  ///     </QualifierReferredTo>
  ///   </HeadingMappedTo>
  /// </HeadingMappedToList>
  /// ```
  ///
  /// @param reader XML 流读取器（已定位到 HeadingMappedToList）
  /// @return 映射关系列表
  /// @throws XMLStreamException XML 解析异常
  public static List<HeadingMappedTo> parseHeadingMappedTos(XMLStreamReader reader)
      throws XMLStreamException {
    List<HeadingMappedTo> mappings = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.HEADING_MAPPED_TO.equals(reader.getLocalName())) {
        HeadingMappedTo mapping = parseSingleHeadingMappedTo(reader);
        if (mapping != null) {
          mappings.add(mapping);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.HEADING_MAPPED_TO_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return mappings;
  }

  /// 解析单个 HeadingMappedTo 元素。
  private static HeadingMappedTo parseSingleHeadingMappedTo(XMLStreamReader reader)
      throws XMLStreamException {
    ReferredTo descriptorRef = ReferredTo.empty();
    ReferredTo qualifierRef = ReferredTo.empty();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO.equals(localName)) {
          descriptorRef = ReferredTo.parseDescriptor(reader);
        } else if (MeshXmlElements.Referred.QUALIFIER_REFERRED_TO.equals(localName)) {
          qualifierRef = ReferredTo.parseQualifier(reader);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.HEADING_MAPPED_TO.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段：DescriptorUI 必须存在
    if (descriptorRef.isValid()) {
      try {
        return HeadingMappedTo.of(
            descriptorRef.toMeshUI(),
            qualifierRef.isValid() ? qualifierRef.toMeshUI() : null,
            descriptorRef.isMajorTopic());
      } catch (Exception e) {
        log.warn("解析 HeadingMappedTo 失败: {}", descriptorRef, e);
      }
    }

    return null;
  }

  /// 解析 SourceList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <SourceList>
  ///   <Source>FDA Substance Registration System (23 Jun 2023)</Source>
  ///   <Source>CAS Registry</Source>
  /// </SourceList>
  /// ```
  ///
  /// @param reader XML 流读取器（已定位到 SourceList）
  /// @return 来源列表
  /// @throws XMLStreamException XML 解析异常
  public static List<ScrSource> parseSources(XMLStreamReader reader) throws XMLStreamException {
    List<ScrSource> sources = new ArrayList<>();
    int orderNum = 0;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.SOURCE.equals(reader.getLocalName())) {
        String sourceText = reader.getElementText().trim();
        if (!sourceText.isEmpty()) {
          try {
            sources.add(ScrSource.of(sourceText, orderNum++));
          } catch (Exception e) {
            log.warn("解析 Source 失败: {}", sourceText, e);
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.SOURCE_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return sources;
  }

  /// 解析 IndexingInformationList 元素。
  ///
  /// **XML 结构**：
  /// ```xml
  /// <IndexingInformationList>
  ///   <IndexingInformation>
  ///     <DescriptorReferredTo>...</DescriptorReferredTo>
  ///     <QualifierReferredTo>...</QualifierReferredTo>  <!-- 可选 -->
  ///   </IndexingInformation>
  /// </IndexingInformationList>
  /// ```
  ///
  /// @param reader XML 流读取器（已定位到 IndexingInformationList）
  /// @return 索引信息列表
  /// @throws XMLStreamException XML 解析异常
  public static List<IndexingInfo> parseIndexingInfos(XMLStreamReader reader)
      throws XMLStreamException {
    List<IndexingInfo> infos = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.INDEXING_INFORMATION.equals(reader.getLocalName())) {
        IndexingInfo info = parseSingleIndexingInfo(reader);
        if (info != null) {
          infos.add(info);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.INDEXING_INFORMATION_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return infos;
  }

  /// 解析单个 IndexingInformation 元素。
  private static IndexingInfo parseSingleIndexingInfo(XMLStreamReader reader)
      throws XMLStreamException {
    ReferredTo descriptorRef = ReferredTo.empty();
    ReferredTo qualifierRef = ReferredTo.empty();
    ReferredTo chemicalRef = ReferredTo.empty();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO.equals(localName)) {
          descriptorRef = ReferredTo.parseDescriptor(reader);
        } else if (MeshXmlElements.Referred.QUALIFIER_REFERRED_TO.equals(localName)) {
          qualifierRef = ReferredTo.parseQualifier(reader);
        } else if (MeshXmlElements.Referred.SUPPLEMENTAL_RECORD_REFERRED_TO.equals(localName)) {
          chemicalRef = ReferredTo.parseSupplemental(reader);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.INDEXING_INFORMATION.equals(reader.getLocalName())) {
        break;
      }
    }

    // IndexingInfo 至少需要一个引用
    if (descriptorRef.isValid() || qualifierRef.isValid() || chemicalRef.isValid()) {
      try {
        return IndexingInfo.of(
            descriptorRef.isValid() ? descriptorRef.toMeshUI() : null,
            qualifierRef.isValid() ? qualifierRef.toMeshUI() : null,
            chemicalRef.isValid() ? chemicalRef.toMeshUI() : null);
      } catch (Exception e) {
        log.warn("解析 IndexingInfo 失败", e);
      }
    }

    return null;
  }
}
