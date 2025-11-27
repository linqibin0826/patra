package com.patra.catalog.infra.adapter.parser.strategy;

import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.adapter.parser.MeshXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingHelper;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// Concept 解析策略。
///
/// 解析 MeSH XML 中的 `<Concept>` 元素，创建 `MeshConcept` 领域实体。
///
/// **XML 结构**：
/// ```xml
/// <Concept PreferredConceptYN="Y">
///   <ConceptUI>M0000001</ConceptUI>
///   <ConceptName>
///     <String>Aspirin</String>
///   </ConceptName>
///   <ScopeNote>A widely used analgesic</ScopeNote>
///   <CASN1Name>2-(Acetyloxy)benzoic Acid</CASN1Name>
///   <RegistryNumber>50-78-2</RegistryNumber>
///   <RegistryNumberList>
///     <RegistryNumber>50-78-2</RegistryNumber>
///   </RegistryNumberList>
///   <ConceptStatus>Active</ConceptStatus>
///   <TranslatorsEnglishScopeNote>...</TranslatorsEnglishScopeNote>
///   <TranslatorsScopeNote>...</TranslatorsScopeNote>
///   <RelatedRegistryNumberList>
///     <RelatedRegistryNumber>...</RelatedRegistryNumber>
///   </RelatedRegistryNumberList>
///   <ConceptRelationList>
///     <ConceptRelation RelationName="NRW">
///       <Concept1UI>M0000001</Concept1UI>
///       <Concept2UI>M0353609</Concept2UI>
///     </ConceptRelation>
///   </ConceptRelationList>
/// </Concept>
/// ```
///
/// **属性解析**：
/// - `PreferredConceptYN`: 是否首选概念（默认 false）
///
/// **注意**：此策略用于独立解析 Concept 流，TermList 由 DescriptorParsingStrategy 单独处理。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class ConceptParsingStrategy implements RecordParsingStrategy<MeshConcept> {

  /// 单例实例。
  public static final ConceptParsingStrategy INSTANCE = new ConceptParsingStrategy();

  private ConceptParsingStrategy() {}

  @Override
  public String rootElementName() {
    return MeshXmlElements.Record.CONCEPT;
  }

  /// 解析单个 Concept 元素。
  ///
  /// @param reader XML 流读取器（已定位到 Concept 元素）
  /// @param context 解析上下文（本策略未使用）
  /// @return MeshConcept 实体，缺少必填字段时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public MeshConcept parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    // 解析属性
    boolean isPreferred =
        XmlParsingHelper.parseYesNoAttribute(
            reader, MeshXmlElements.Attribute.PREFERRED_CONCEPT_YN, false);

    // 子元素字段
    String conceptUi = null;
    String conceptName = null;
    String scopeNote = null;
    String casn1Name = null;
    String conceptStatus = null;
    String translatorsEnglishScopeNote = null;
    String translatorsScopeNote = null;
    List<String> registryNumbers = new ArrayList<>();
    List<String> relatedRegistryNumbers = new ArrayList<>();
    List<ConceptRelation> conceptRelations = new ArrayList<>();

    // 解析子元素
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case "ConceptUI" -> conceptUi = reader.getElementText();
          case "ConceptName" -> conceptName = XmlParsingHelper.parseNameElement(reader);
          case "ScopeNote" -> scopeNote = reader.getElementText();
          case "CASN1Name" -> casn1Name = reader.getElementText();
          case "ConceptStatus" -> conceptStatus = reader.getElementText();
          case "TranslatorsEnglishScopeNote" -> translatorsEnglishScopeNote =
              reader.getElementText();
          case "TranslatorsScopeNote" -> translatorsScopeNote = reader.getElementText();
          case "RegistryNumber" -> {
            // 单个 RegistryNumber（旧版 DTD）
            String regNum = reader.getElementText();
            if (regNum != null && !regNum.trim().isEmpty()) {
              registryNumbers.add(regNum.trim());
            }
          }
          case "RegistryNumberList" -> registryNumbers.addAll(
              parseRegistryNumberList(reader));
          case "RelatedRegistryNumberList" -> relatedRegistryNumbers.addAll(
              parseRelatedRegistryNumberList(reader));
          case "ConceptRelationList" -> conceptRelations.addAll(
              parseConceptRelationList(reader));
          case "TermList" -> {
            // 独立解析 Concept 时跳过 TermList（由 DescriptorParsingStrategy 处理）
            XmlParsingHelper.skipElement(reader, MeshXmlElements.List.TERM_LIST);
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Record.CONCEPT.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段
    if (conceptUi == null || conceptName == null) {
      log.warn("跳过无效 Concept（缺少必填字段）: UI={}, Name={}", conceptUi, conceptName);
      return null;
    }

    // 创建实体
    MeshConcept concept =
        MeshConcept.create(MeshUI.of(conceptUi), conceptName, isPreferred);

    // 设置可选字段
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

    // 添加集合数据
    if (!relatedRegistryNumbers.isEmpty()) {
      concept.addRelatedRegistryNumbers(relatedRegistryNumbers);
    }
    if (!conceptRelations.isEmpty()) {
      concept.addConceptRelations(conceptRelations);
    }

    return concept;
  }

  // ========== 私有解析方法 ==========

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

  /// 解析 ConceptRelationList。
  private List<ConceptRelation> parseConceptRelationList(XMLStreamReader reader)
      throws XMLStreamException {
    List<ConceptRelation> relations = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.CONCEPT_RELATION.equals(reader.getLocalName())) {
        ConceptRelation relation = parseConceptRelation(reader);
        if (relation != null) {
          relations.add(relation);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.CONCEPT_RELATION_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
    return relations;
  }

  /// 解析单个 ConceptRelation。
  private ConceptRelation parseConceptRelation(XMLStreamReader reader) throws XMLStreamException {
    String relationName = reader.getAttributeValue(null, MeshXmlElements.Attribute.RELATION_NAME);
    String concept1Ui = null;
    String concept2Ui = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case "Concept1UI" -> concept1Ui = reader.getElementText();
          case "Concept2UI" -> concept2Ui = reader.getElementText();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Other.CONCEPT_RELATION.equals(reader.getLocalName())) {
        break;
      }
    }

    // 使用 ofNullable 允许 relationName 为 null（DTD 定义为 #IMPLIED）
    if (concept1Ui != null && concept2Ui != null) {
      try {
        return ConceptRelation.ofNullable(
            MeshUI.of(concept1Ui), MeshUI.of(concept2Ui), relationName);
      } catch (Exception e) {
        log.warn(
            "解析 ConceptRelation 失败: RelationName={}, Concept1UI={}, Concept2UI={}",
            relationName,
            concept1Ui,
            concept2Ui,
            e);
      }
    }
    return null;
  }
}
