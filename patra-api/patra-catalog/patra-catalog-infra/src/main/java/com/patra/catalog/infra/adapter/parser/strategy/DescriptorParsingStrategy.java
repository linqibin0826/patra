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
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingHelper;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// DescriptorRecord 解析策略。
///
/// 解析 MeSH XML 中的 `<DescriptorRecord>` 元素，创建 `MeshDescriptorAggregate` 聚合根。
/// 这是最复杂的策略，需要处理多种嵌套列表和关联实体。
///
/// **XML 结构**：
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
/// @author linqibin
/// @since 0.1.0
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
  /// @param reader XML 流读取器（已定位到 DescriptorRecord 元素）
  /// @param context 解析上下文（包含 meshVersion）
  /// @return MeshDescriptorAggregate 聚合根，缺少必填字段时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public MeshDescriptorAggregate parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    // 解析 DescriptorClass 属性
    DescriptorClass descriptorClass = DescriptorClass.TOPICAL;
    String descriptorClassAttr =
        reader.getAttributeValue(null, MeshXmlElements.Attribute.DESCRIPTOR_CLASS);
    if (descriptorClassAttr != null) {
      try {
        descriptorClass = DescriptorClass.fromCode(descriptorClassAttr);
      } catch (IllegalArgumentException e) {
        log.warn("未知的 DescriptorClass 值：{}，使用默认值 TOPICAL", descriptorClassAttr);
      }
    }

    // 基本字段
    String descriptorUI = null;
    String descriptorName = null;

    // 日期字段
    String dateCreated = null;
    String dateRevised = null;
    String dateEstablished = null;

    // 文本字段
    String historyNote = null;
    String onlineNote = null;
    String publicMeshNote = null;
    String nlmClassificationNumber = null;
    String annotation = null;
    String considerAlso = null;
    String scopeNote = null;

    // 集合字段
    List<MeshTreeNumber> treeNumbers = new ArrayList<>();
    List<AllowableQualifier> allowableQualifiers = new ArrayList<>();
    List<PharmacologicalAction> pharmacologicalActions = new ArrayList<>();
    List<String> previousIndexings = new ArrayList<>();
    List<SeeRelatedDescriptor> seeRelatedDescriptors = new ArrayList<>();
    List<MeshConcept> concepts = new ArrayList<>();
    List<MeshEntryTerm> entryTerms = new ArrayList<>();
    List<EntryCombination> entryCombinations = new ArrayList<>();

    // 解析子元素
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Identifier.DESCRIPTOR_UI -> descriptorUI = reader.getElementText();
          case MeshXmlElements.Name.DESCRIPTOR_NAME ->
              descriptorName = XmlParsingHelper.parseNameElement(reader);
          case MeshXmlElements.Date.DATE_CREATED ->
              dateCreated = XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_CREATED);
          case MeshXmlElements.Date.DATE_REVISED ->
              dateRevised = XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_REVISED);
          case MeshXmlElements.Date.DATE_ESTABLISHED ->
              dateEstablished =
                  XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_ESTABLISHED);
          case MeshXmlElements.Other.HISTORY_NOTE -> historyNote = reader.getElementText().trim();
          case MeshXmlElements.Other.ONLINE_NOTE -> onlineNote = reader.getElementText().trim();
          case MeshXmlElements.Other.PUBLIC_MESH_NOTE ->
              publicMeshNote = reader.getElementText().trim();
          case MeshXmlElements.Other.NLM_CLASSIFICATION_NUMBER ->
              nlmClassificationNumber = reader.getElementText().trim();
          case MeshXmlElements.Other.ANNOTATION -> annotation = reader.getElementText().trim();
          case MeshXmlElements.Other.CONSIDER_ALSO -> considerAlso = reader.getElementText().trim();
          case MeshXmlElements.Other.SCOPE_NOTE -> scopeNote = reader.getElementText().trim();
          case MeshXmlElements.List.TREE_NUMBER_LIST -> treeNumbers = parseTreeNumberList(reader);
          case MeshXmlElements.List.ALLOWABLE_QUALIFIERS_LIST ->
              allowableQualifiers = parseAllowableQualifiersList(reader);
          case MeshXmlElements.List.PHARMACOLOGICAL_ACTION_LIST ->
              pharmacologicalActions = parsePharmacologicalActionList(reader);
          case MeshXmlElements.List.PREVIOUS_INDEXING_LIST ->
              previousIndexings = parsePreviousIndexingList(reader);
          case MeshXmlElements.List.SEE_RELATED_LIST ->
              seeRelatedDescriptors = parseSeeRelatedList(reader);
          case MeshXmlElements.List.ENTRY_COMBINATION_LIST ->
              entryCombinations = parseEntryCombinationList(reader);
          case MeshXmlElements.List.CONCEPT_LIST ->
              parseConceptListIntoAggregate(reader, concepts, entryTerms);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Record.DESCRIPTOR.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段
    if (descriptorUI == null || descriptorName == null) {
      log.warn("跳过无效 Descriptor（缺少必填字段）: UI={}, Name={}", descriptorUI, descriptorName);
      return null;
    }

    // 创建聚合根
    MeshDescriptorAggregate aggregate =
        MeshDescriptorAggregate.create(
            MeshUI.of(descriptorUI), descriptorName, descriptorClass, context.meshVersion());

    // 设置日期字段
    if (dateCreated != null) {
      aggregate.setDateCreated(dateCreated);
    }
    if (dateRevised != null) {
      aggregate.setDateRevised(dateRevised);
    }
    if (dateEstablished != null) {
      aggregate.setDateEstablished(dateEstablished);
    }

    // 设置文本字段
    if (historyNote != null) {
      aggregate.setHistoryNote(historyNote);
    }
    if (onlineNote != null) {
      aggregate.setOnlineNote(onlineNote);
    }
    if (nlmClassificationNumber != null) {
      aggregate.setNlmClassificationNumber(nlmClassificationNumber);
    }
    if (annotation != null) {
      aggregate.setAnnotation(annotation);
    }
    if (considerAlso != null) {
      aggregate.setConsiderAlso(considerAlso);
    }
    if (scopeNote != null) {
      aggregate.setScopeNote(scopeNote);
    }
    if (publicMeshNote != null) {
      aggregate.setPublicMeshNote(publicMeshNote);
    }

    // 添加集合数据
    if (!treeNumbers.isEmpty()) {
      aggregate.addTreeNumbers(treeNumbers);
    }
    if (!allowableQualifiers.isEmpty()) {
      aggregate.addAllowableQualifiers(allowableQualifiers);
    }
    if (!pharmacologicalActions.isEmpty()) {
      aggregate.addPharmacologicalActions(pharmacologicalActions);
    }
    if (!previousIndexings.isEmpty()) {
      aggregate.addPreviousIndexings(previousIndexings);
    }
    if (!seeRelatedDescriptors.isEmpty()) {
      aggregate.addSeeRelatedDescriptors(seeRelatedDescriptors);
    }
    if (!concepts.isEmpty()) {
      aggregate.addConcepts(concepts);
    }
    if (!entryTerms.isEmpty()) {
      aggregate.addEntryTerms(entryTerms);
    }
    if (!entryCombinations.isEmpty()) {
      aggregate.addEntryCombinations(entryCombinations);
    }

    return aggregate;
  }

  // ========== 列表解析方法 ==========

  /// 解析 TreeNumberList，第一个标记为 isPrimary。
  private List<MeshTreeNumber> parseTreeNumberList(XMLStreamReader reader)
      throws XMLStreamException {
    List<MeshTreeNumber> treeNumbers = new ArrayList<>();
    int index = 0;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Record.TREE_NUMBER.equals(reader.getLocalName())) {
        String treeNumber = reader.getElementText();
        treeNumbers.add(MeshTreeNumber.create(treeNumber, index == 0));
        index++;
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.TREE_NUMBER_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
    return treeNumbers;
  }

  /// 解析 AllowableQualifiersList。
  private List<AllowableQualifier> parseAllowableQualifiersList(XMLStreamReader reader)
      throws XMLStreamException {
    List<AllowableQualifier> qualifiers = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.ALLOWABLE_QUALIFIER.equals(reader.getLocalName())) {
        AllowableQualifier qualifier = parseAllowableQualifier(reader);
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

  /// 解析单个 AllowableQualifier。
  private AllowableQualifier parseAllowableQualifier(XMLStreamReader reader)
      throws XMLStreamException {
    String qualifierUi = null;
    String qualifierName = null;
    String abbreviation = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Referred.QUALIFIER_REFERRED_TO -> {
            // 解析嵌套的 QualifierReferredTo
            var result = parseQualifierReferredTo(reader);
            qualifierUi = result[0];
            qualifierName = result[1];
          }
          case MeshXmlElements.Other.ABBREVIATION -> abbreviation = reader.getElementText();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.ALLOWABLE_QUALIFIER.equals(reader.getLocalName())) {
        break;
      }
    }

    if (qualifierUi != null && qualifierName != null && abbreviation != null) {
      try {
        return AllowableQualifier.of(MeshUI.of(qualifierUi), qualifierName, abbreviation);
      } catch (Exception e) {
        log.warn("解析 AllowableQualifier 失败: UI={}, Name={}", qualifierUi, qualifierName, e);
      }
    }
    return null;
  }

  /// 解析 QualifierReferredTo，返回 [UI, Name]。
  private String[] parseQualifierReferredTo(XMLStreamReader reader) throws XMLStreamException {
    String qualifierUi = null;
    String qualifierName = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Identifier.QUALIFIER_UI -> qualifierUi = reader.getElementText();
          case MeshXmlElements.Name.QUALIFIER_NAME ->
              qualifierName = XmlParsingHelper.parseNameElement(reader);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Referred.QUALIFIER_REFERRED_TO.equals(reader.getLocalName())) {
        break;
      }
    }
    return new String[] {qualifierUi, qualifierName};
  }

  /// 解析 PharmacologicalActionList。
  private List<PharmacologicalAction> parsePharmacologicalActionList(XMLStreamReader reader)
      throws XMLStreamException {
    List<PharmacologicalAction> actions = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.PHARMACOLOGICAL_ACTION.equals(reader.getLocalName())) {
        PharmacologicalAction action = parsePharmacologicalAction(reader);
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

  /// 解析单个 PharmacologicalAction。
  private PharmacologicalAction parsePharmacologicalAction(XMLStreamReader reader)
      throws XMLStreamException {
    String descriptorUi = null;
    String descriptorName = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO.equals(reader.getLocalName())) {
        var result = parseDescriptorReferredTo(reader);
        descriptorUi = result[0];
        descriptorName = result[1];
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.PHARMACOLOGICAL_ACTION.equals(reader.getLocalName())) {
        break;
      }
    }

    if (descriptorUi != null && descriptorName != null) {
      try {
        return PharmacologicalAction.of(MeshUI.of(descriptorUi), descriptorName);
      } catch (Exception e) {
        log.warn("解析 PharmacologicalAction 失败: UI={}, Name={}", descriptorUi, descriptorName, e);
      }
    }
    return null;
  }

  /// 解析 DescriptorReferredTo，返回 [UI, Name]。
  private String[] parseDescriptorReferredTo(XMLStreamReader reader) throws XMLStreamException {
    String descriptorUi = null;
    String descriptorName = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Identifier.DESCRIPTOR_UI -> descriptorUi = reader.getElementText();
          case MeshXmlElements.Name.DESCRIPTOR_NAME ->
              descriptorName = XmlParsingHelper.parseNameElement(reader);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO.equals(reader.getLocalName())) {
        break;
      }
    }
    return new String[] {descriptorUi, descriptorName};
  }

  /// 解析 PreviousIndexingList。
  private List<String> parsePreviousIndexingList(XMLStreamReader reader) throws XMLStreamException {
    List<String> indexings = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.PREVIOUS_INDEXING.equals(reader.getLocalName())) {
        String text = reader.getElementText();
        if (text != null && !text.trim().isEmpty()) {
          indexings.add(text.trim());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.PREVIOUS_INDEXING_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
    return indexings;
  }

  /// 解析 SeeRelatedList。
  private List<SeeRelatedDescriptor> parseSeeRelatedList(XMLStreamReader reader)
      throws XMLStreamException {
    List<SeeRelatedDescriptor> descriptors = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.SEE_RELATED_DESCRIPTOR.equals(reader.getLocalName())) {
        SeeRelatedDescriptor descriptor = parseSeeRelatedDescriptor(reader);
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

  /// 解析单个 SeeRelatedDescriptor。
  private SeeRelatedDescriptor parseSeeRelatedDescriptor(XMLStreamReader reader)
      throws XMLStreamException {
    String descriptorUi = null;
    String descriptorName = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO.equals(reader.getLocalName())) {
        var result = parseDescriptorReferredTo(reader);
        descriptorUi = result[0];
        descriptorName = result[1];
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.SEE_RELATED_DESCRIPTOR.equals(reader.getLocalName())) {
        break;
      }
    }

    if (descriptorUi != null && descriptorName != null) {
      try {
        return SeeRelatedDescriptor.of(MeshUI.of(descriptorUi), descriptorName);
      } catch (Exception e) {
        log.warn("解析 SeeRelatedDescriptor 失败: UI={}, Name={}", descriptorUi, descriptorName, e);
      }
    }
    return null;
  }

  /// 解析 EntryCombinationList。
  private List<EntryCombination> parseEntryCombinationList(XMLStreamReader reader)
      throws XMLStreamException {
    List<EntryCombination> combinations = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.ENTRY_COMBINATION.equals(reader.getLocalName())) {
        EntryCombination combination = parseEntryCombination(reader);
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

  /// 解析单个 EntryCombination。
  private EntryCombination parseEntryCombination(XMLStreamReader reader) throws XMLStreamException {
    String ecinDescriptorUi = null;
    String ecinQualifierUi = null;
    String ecoutDescriptorUi = null;
    String ecoutQualifierUi = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Referred.ECIN -> {
            var result = parseEcElement(reader, MeshXmlElements.Referred.ECIN);
            ecinDescriptorUi = result[0];
            ecinQualifierUi = result[1];
          }
          case MeshXmlElements.Referred.ECOUT -> {
            var result = parseEcElement(reader, MeshXmlElements.Referred.ECOUT);
            ecoutDescriptorUi = result[0];
            ecoutQualifierUi = result[1];
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.ENTRY_COMBINATION.equals(reader.getLocalName())) {
        break;
      }
    }

    if (ecinDescriptorUi != null && ecinQualifierUi != null && ecoutDescriptorUi != null) {
      try {
        return EntryCombination.of(
            MeshUI.of(ecinDescriptorUi),
            MeshUI.of(ecinQualifierUi),
            MeshUI.of(ecoutDescriptorUi),
            ecoutQualifierUi != null ? MeshUI.of(ecoutQualifierUi) : null);
      } catch (Exception e) {
        log.warn("解析 EntryCombination 失败", e);
      }
    }
    return null;
  }

  /// 解析 ECIN 或 ECOUT 元素，返回 [DescriptorUI, QualifierUI]。
  private String[] parseEcElement(XMLStreamReader reader, String elementName)
      throws XMLStreamException {
    String descriptorUi = null;
    String qualifierUi = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO -> {
            var result = parseDescriptorReferredTo(reader);
            descriptorUi = result[0];
          }
          case MeshXmlElements.Referred.QUALIFIER_REFERRED_TO -> {
            var result = parseQualifierReferredTo(reader);
            qualifierUi = result[0];
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && elementName.equals(reader.getLocalName())) {
        break;
      }
    }
    return new String[] {descriptorUi, qualifierUi};
  }

  // ========== ConceptList 解析 ==========

  /// 解析 ConceptList，提取 Concept 和 EntryTerm。
  private void parseConceptListIntoAggregate(
      XMLStreamReader reader, List<MeshConcept> concepts, List<MeshEntryTerm> entryTerms)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Record.CONCEPT.equals(reader.getLocalName())) {
        parseConceptAndTerms(reader, concepts, entryTerms);
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.CONCEPT_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
  }

  /// 解析单个 Concept 及其 TermList。
  private void parseConceptAndTerms(
      XMLStreamReader reader, List<MeshConcept> concepts, List<MeshEntryTerm> entryTerms)
      throws XMLStreamException {
    // 使用 ConceptParsingStrategy 的逻辑，但需要额外提取 TermList
    boolean isPreferred =
        XmlParsingHelper.parseYesNoAttribute(
            reader, MeshXmlElements.Attribute.PREFERRED_CONCEPT_YN, false);

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
            if (regNum != null && !regNum.trim().isEmpty()) {
              registryNumbers.add(regNum.trim());
            }
          }
          case MeshXmlElements.List.REGISTRY_NUMBER_LIST ->
              registryNumbers.addAll(parseRegistryNumberList(reader));
          case MeshXmlElements.List.RELATED_REGISTRY_NUMBER_LIST ->
              relatedRegistryNumbers.addAll(parseRelatedRegistryNumberList(reader));
          case MeshXmlElements.List.TERM_LIST ->
              parseTermListIntoEntryTerms(reader, entryTerms, conceptUi);
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
  private List<String> parseRegistryNumberList(XMLStreamReader reader) throws XMLStreamException {
    List<String> registryNumbers = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.REGISTRY_NUMBER.equals(reader.getLocalName())) {
        String regNum = reader.getElementText();
        if (regNum != null && !regNum.trim().isEmpty()) {
          registryNumbers.add(regNum.trim());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.REGISTRY_NUMBER_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
    return registryNumbers;
  }

  /// 解析 RelatedRegistryNumberList。
  private List<String> parseRelatedRegistryNumberList(XMLStreamReader reader)
      throws XMLStreamException {
    List<String> registryNumbers = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.RELATED_REGISTRY_NUMBER.equals(reader.getLocalName())) {
        String regNum = reader.getElementText();
        if (regNum != null && !regNum.trim().isEmpty()) {
          registryNumbers.add(regNum.trim());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.RELATED_REGISTRY_NUMBER_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
    return registryNumbers;
  }

  /// 解析 TermList 并添加到 EntryTerms。
  private void parseTermListIntoEntryTerms(
      XMLStreamReader reader, List<MeshEntryTerm> entryTerms, String conceptUi)
      throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Record.TERM.equals(reader.getLocalName())) {
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
}
