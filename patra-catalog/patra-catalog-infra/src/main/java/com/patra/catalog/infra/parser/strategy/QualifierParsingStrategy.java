package com.patra.catalog.infra.parser.strategy;

import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.parser.MeshXmlElements;
import com.patra.catalog.infra.parser.support.XmlParsingContext;
import com.patra.catalog.infra.parser.support.XmlParsingHelper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// QualifierRecord 解析策略。
///
/// 解析 MeSH XML 中的 `<QualifierRecord>` 元素，创建 `MeshQualifierAggregate` 聚合根。
///
/// **XML 结构**：
/// ```xml
/// <QualifierRecord>
///   <QualifierUI>Q000001</QualifierUI>
///   <QualifierName>
///     <String>diagnosis</String>
///   </QualifierName>
///   <Annotation>Used with diseases...</Annotation>
///   <DateCreated><Year>1966</Year><Month>01</Month><Day>01</Day></DateCreated>
///   <DateRevised>...</DateRevised>
///   <DateEstablished>...</DateEstablished>
///   <HistoryNote>66; used with Category A-D 1966-74</HistoryNote>
///   <OnlineNote>search policy: Online Manual</OnlineNote>
///   <TreeNumberList>
///     <TreeNumber>Y01.060</TreeNumber>
///   </TreeNumberList>
///   <ConceptList>
///     <Concept PreferredConceptYN="Y">
///       <TermList>
///         <Term RecordPreferredTermYN="Y">
///           <Abbreviation>DI</Abbreviation>
///         </Term>
///       </TermList>
///     </Concept>
///   </ConceptList>
/// </QualifierRecord>
/// ```
///
/// **必填字段**：
/// - `QualifierUI`: 限定词唯一标识符
/// - `QualifierName`: 限定词名称
/// - `Abbreviation`: 从 ConceptList 中首选概念的首选术语提取
///
/// **上下文使用**：
/// - `meshVersion`: 从 XmlParsingContext 获取，用于设置 MeSH 版本
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class QualifierParsingStrategy
    implements RecordParsingStrategy<MeshQualifierAggregate> {

  /// 单例实例。
  public static final QualifierParsingStrategy INSTANCE = new QualifierParsingStrategy();

  private QualifierParsingStrategy() {}

  @Override
  public String rootElementName() {
    return MeshXmlElements.Record.QUALIFIER;
  }

  /// 解析单个 QualifierRecord 元素。
  ///
  /// @param reader XML 流读取器（已定位到 QualifierRecord 元素）
  /// @param context 解析上下文（包含 meshVersion）
  /// @return MeshQualifierAggregate 聚合根，缺少必填字段时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public MeshQualifierAggregate parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    // 子元素字段
    String qualifierUi = null;
    String name = null;
    String abbreviation = null;
    String annotation = null;
    LocalDate dateCreated = null;
    LocalDate dateRevised = null;
    LocalDate dateEstablished = null;
    String historyNote = null;
    String onlineNote = null;
    List<String> treeNumbers = new ArrayList<>();

    // 解析子元素
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Identifier.QUALIFIER_UI -> qualifierUi = reader.getElementText();
          case MeshXmlElements.Name.QUALIFIER_NAME ->
              name = XmlParsingHelper.parseNameElement(reader);
          case MeshXmlElements.Other.ANNOTATION -> annotation = reader.getElementText();
          case MeshXmlElements.Date.DATE_CREATED ->
              dateCreated = XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_CREATED);
          case MeshXmlElements.Date.DATE_REVISED ->
              dateRevised = XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_REVISED);
          case MeshXmlElements.Date.DATE_ESTABLISHED ->
              dateEstablished =
                  XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_ESTABLISHED);
          case MeshXmlElements.Other.HISTORY_NOTE -> historyNote = reader.getElementText().trim();
          case MeshXmlElements.Other.ONLINE_NOTE -> onlineNote = reader.getElementText().trim();
          case MeshXmlElements.List.TREE_NUMBER_LIST -> treeNumbers = parseTreeNumberList(reader);
          case MeshXmlElements.List.CONCEPT_LIST ->
              abbreviation = extractAbbreviationFromPreferredTerm(reader);
          default -> {
            // 跳过其他未处理的元素
            XmlParsingHelper.skipElement(reader, localName);
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Record.QUALIFIER.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段
    if (qualifierUi == null) {
      log.warn("跳过 Qualifier 记录：缺少 QualifierUI");
      return null;
    }
    if (name == null) {
      log.warn("跳过 Qualifier 记录：UI={}, 缺少 QualifierName", qualifierUi);
      return null;
    }
    if (abbreviation == null || abbreviation.isBlank()) {
      log.warn("跳过 Qualifier 记录：UI={}, 缺少 Abbreviation（preferred term 无缩写）", qualifierUi);
      return null;
    }

    // 创建聚合根（不含版本号，由调用方设置）
    return MeshQualifierAggregate.create(MeshUI.of(qualifierUi), name, abbreviation)
        .withAnnotation(annotation)
        .withDateCreated(dateCreated)
        .withDateRevised(dateRevised)
        .withDateEstablished(dateEstablished)
        .withHistoryNote(historyNote)
        .withOnlineNote(onlineNote)
        .withTreeNumbers(treeNumbers);
  }

  // ========== 私有解析方法 ==========

  /// 解析 TreeNumberList 元素。
  private List<String> parseTreeNumberList(XMLStreamReader reader) throws XMLStreamException {
    List<String> treeNumbers = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Record.TREE_NUMBER.equals(reader.getLocalName())) {
        treeNumbers.add(reader.getElementText());
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.TREE_NUMBER_LIST.equals(reader.getLocalName())) {
        break;
      }
    }
    return treeNumbers;
  }

  /// 从 ConceptList 的首选术语中提取 Abbreviation。
  ///
  /// 查找 PreferredConceptYN="Y" 的 Concept，
  /// 然后在其 TermList 中找到 RecordPreferredTermYN="Y" 的 Term，
  /// 提取其 Abbreviation 元素。
  private String extractAbbreviationFromPreferredTerm(XMLStreamReader reader)
      throws XMLStreamException {
    String abbreviation = null;
    boolean inPreferredConcept = false;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (MeshXmlElements.Record.CONCEPT.equals(localName)) {
          // 检查是否为 preferred concept
          String preferredAttr =
              reader.getAttributeValue(null, MeshXmlElements.Attribute.PREFERRED_CONCEPT_YN);
          inPreferredConcept = "Y".equals(preferredAttr);
        } else if (MeshXmlElements.Record.TERM.equals(localName) && inPreferredConcept) {
          // 检查是否为 record preferred term
          String recordPreferredAttr =
              reader.getAttributeValue(null, MeshXmlElements.Attribute.RECORD_PREFERRED_TERM_YN);
          if ("Y".equals(recordPreferredAttr)) {
            // 进入 Term 元素，查找 Abbreviation
            abbreviation = extractAbbreviationFromTerm(reader);
            if (abbreviation != null) {
              // 找到后跳过剩余的 ConceptList
              skipToEndOfConceptList(reader);
              return abbreviation;
            }
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.List.CONCEPT_LIST.equals(reader.getLocalName())) {
        break;
      }
    }

    return abbreviation;
  }

  /// 从 Term 元素中提取 Abbreviation。
  private String extractAbbreviationFromTerm(XMLStreamReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && MeshXmlElements.Other.ABBREVIATION.equals(reader.getLocalName())) {
        return reader.getElementText();
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Record.TERM.equals(reader.getLocalName())) {
        break;
      }
    }
    return null;
  }

  /// 跳过剩余的 ConceptList 内容。
  private void skipToEndOfConceptList(XMLStreamReader reader) throws XMLStreamException {
    int depth = 1; // 已经在 ConceptList 内部
    while (reader.hasNext() && depth > 0) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (MeshXmlElements.List.CONCEPT_LIST.equals(reader.getLocalName())) {
          depth++;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        if (MeshXmlElements.List.CONCEPT_LIST.equals(reader.getLocalName())) {
          depth--;
        }
      }
    }
  }
}
