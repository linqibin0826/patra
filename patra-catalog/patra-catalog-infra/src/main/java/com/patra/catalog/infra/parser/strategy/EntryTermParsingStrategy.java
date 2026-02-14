package com.patra.catalog.infra.parser.strategy;

import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.parser.MeshXmlElements;
import com.patra.catalog.infra.parser.support.XmlParsingContext;
import com.patra.catalog.infra.parser.support.XmlParsingHelper;
import java.time.LocalDate;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// EntryTerm 解析策略。
///
/// 解析 MeSH XML 中的 `<Term>` 元素，创建 `MeshEntryTerm` 领域实体。
///
/// **XML 结构**：
/// ```xml
/// <Term RecordPreferredTermYN="Y" ConceptPreferredTermYN="Y"
///       IsPermutedTermYN="N" LexicalTag="NON" PrintFlagYN="Y">
///   <TermUI>T000001</TermUI>
///   <String>Term Name</String>
///   <DateCreated><Year>2020</Year><Month>1</Month><Day>1</Day></DateCreated>
///   <ThesaurusIDlist>
///     <ThesaurusID>FDA SRS (2014)</ThesaurusID>
///   </ThesaurusIDlist>
///   <EntryVersion>ASPIRIN</EntryVersion>
///   <Abbreviation>ASP</Abbreviation>
///   <SortVersion>ASPIRIN</SortVersion>
///   <TermNote>Some note</TermNote>
/// </Term>
/// ```
///
/// **属性解析**：
/// - `LexicalTag`: 词法标记（默认 "NON"）
/// - `RecordPreferredTermYN`: 是否记录首选（默认 false）
/// - `ConceptPreferredTermYN`: 是否概念首选（默认 false）
/// - `IsPermutedTermYN`: 是否排列术语（默认 false）
/// - `PrintFlagYN`: 是否可打印（默认 true）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class EntryTermParsingStrategy implements RecordParsingStrategy<MeshEntryTerm> {

  /// 单例实例。
  public static final EntryTermParsingStrategy INSTANCE = new EntryTermParsingStrategy();

  private EntryTermParsingStrategy() {}

  @Override
  public String rootElementName() {
    return MeshXmlElements.Record.TERM;
  }

  /// 解析单个 Term 元素。
  ///
  /// @param reader XML 流读取器（已定位到 Term 元素）
  /// @param context 解析上下文（本策略未使用）
  /// @return MeshEntryTerm 实体，缺少必填字段时返回 null
  /// @throws XMLStreamException XML 解析异常
  @Override
  public MeshEntryTerm parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    // 解析属性
    String lexicalTagCode =
        XmlParsingHelper.getAttributeOrDefault(
            reader, MeshXmlElements.Attribute.LEXICAL_TAG, "NON");
    boolean isRecordPreferred =
        XmlParsingHelper.parseYesNoAttribute(
            reader, MeshXmlElements.Attribute.RECORD_PREFERRED_TERM_YN, false);
    boolean isConceptPreferred =
        XmlParsingHelper.parseYesNoAttribute(
            reader, MeshXmlElements.Attribute.CONCEPT_PREFERRED_TERM_YN, false);
    boolean isPermutedTerm =
        XmlParsingHelper.parseYesNoAttribute(
            reader, MeshXmlElements.Attribute.IS_PERMUTED_TERM_YN, false);
    boolean isPrintFlag =
        XmlParsingHelper.parseYesNoAttribute(reader, MeshXmlElements.Attribute.PRINT_FLAG_YN, true);

    // 子元素字段
    String termUI = null;
    String termText = null;
    LocalDate dateCreated = null;
    String entryVersion = null;
    String abbreviation = null;
    String sortVersion = null;
    String termNote = null;
    List<String> thesaurusIds = List.of();

    // 解析子元素
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case MeshXmlElements.Identifier.TERM_UI -> termUI = reader.getElementText();
          case MeshXmlElements.Name.STRING -> termText = reader.getElementText();
          case MeshXmlElements.Date.DATE_CREATED ->
              dateCreated = XmlParsingHelper.parseDate(reader, MeshXmlElements.Date.DATE_CREATED);
          case MeshXmlElements.List.THESAURUS_ID_LIST ->
              thesaurusIds =
                  XmlParsingHelper.parseStringList(
                      reader,
                      MeshXmlElements.List.THESAURUS_ID_LIST,
                      MeshXmlElements.Other.THESAURUS_ID);
          case MeshXmlElements.Other.ENTRY_VERSION -> entryVersion = reader.getElementText();
          case MeshXmlElements.Other.ABBREVIATION -> abbreviation = reader.getElementText();
          case MeshXmlElements.Other.SORT_VERSION -> sortVersion = reader.getElementText();
          case MeshXmlElements.Other.TERM_NOTE -> termNote = reader.getElementText();
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && MeshXmlElements.Record.TERM.equals(reader.getLocalName())) {
        break;
      }
    }

    // 验证必填字段
    if (termText == null) {
      log.warn("跳过无效 EntryTerm（缺少术语文本）");
      return null;
    }

    // 解析词法标记
    LexicalTag lexicalTag;
    try {
      lexicalTag = LexicalTag.fromCode(lexicalTagCode);
    } catch (IllegalArgumentException e) {
      log.warn("未知的词法标记：{}，使用默认值 NON", lexicalTagCode);
      lexicalTag = LexicalTag.NON;
    }

    // 创建实体
    MeshUI meshTermUI = termUI != null ? MeshUI.of(termUI) : null;
    MeshEntryTerm entryTerm =
        MeshEntryTerm.create(
            meshTermUI,
            termText,
            lexicalTag,
            isRecordPreferred,
            isPrintFlag,
            isConceptPreferred,
            isPermutedTerm);

    // 设置可选字段
    if (dateCreated != null) {
      entryTerm.withDateCreated(dateCreated);
    }
    if (entryVersion != null) {
      entryTerm.withEntryVersion(entryVersion);
    }
    if (abbreviation != null) {
      entryTerm.withAbbreviation(abbreviation);
    }
    if (sortVersion != null) {
      entryTerm.withSortVersion(sortVersion);
    }
    if (termNote != null) {
      entryTerm.withTermNote(termNote);
    }
    if (!thesaurusIds.isEmpty()) {
      entryTerm.addThesaurusIds(thesaurusIds);
    }

    return entryTerm;
  }
}
