package com.patra.catalog.infra.parser.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.infra.parser.MeshXmlElements;
import com.patra.catalog.infra.parser.support.XmlParsingContext;
import java.io.StringReader;
import java.time.LocalDate;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// EntryTermParsingStrategy 单元测试。
///
/// 验证入口术语解析策略的正确性。
@DisplayName("EntryTermParsingStrategy 策略")
class EntryTermParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private final EntryTermParsingStrategy strategy = EntryTermParsingStrategy.INSTANCE;

  // ========== 策略契约测试 ==========

  @Nested
  @DisplayName("策略契约")
  class StrategyContract {

    @Test
    @DisplayName("rootElementName() 应返回 Term")
    void rootElementName_shouldReturnTerm() {
      assertEquals(MeshXmlElements.Record.TERM, strategy.rootElementName());
    }

    @Test
    @DisplayName("INSTANCE 应为非空单例")
    void instance_shouldBeNonNullSingleton() {
      assertNotNull(EntryTermParsingStrategy.INSTANCE);
      assertEquals(EntryTermParsingStrategy.INSTANCE, strategy);
    }
  }

  // ========== parseRecord 基本字段测试 ==========

  @Nested
  @DisplayName("parseRecord() 基本字段")
  class ParseRecordBasicFields {

    @Test
    @DisplayName("应提取所有必填字段")
    void shouldExtractAllRequiredFields() throws Exception {
      var xml =
          """
          <Term RecordPreferredTermYN="Y" ConceptPreferredTermYN="Y" IsPermutedTermYN="N" LexicalTag="NON" PrintFlagYN="Y">
            <TermUI>T000001</TermUI>
            <String>Term Name</String>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("T000001", result.getTermUi().ui());
      assertEquals("Term Name", result.getTerm());
      assertTrue(result.isRecordPreferred());
      assertTrue(result.isConceptPreferred());
      assertFalse(result.isPermutedTerm());
      assertEquals(LexicalTag.NON, result.getLexicalTag());
      assertTrue(result.isPrintFlag());
    }

    @Test
    @DisplayName("应正确解析词法标记")
    void shouldParseLexicalTag() throws Exception {
      var xml =
          """
          <Term LexicalTag="ABB">
            <String>ASA</String>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(LexicalTag.ABB, result.getLexicalTag());
    }

    @Test
    @DisplayName("无 LexicalTag 属性时应使用默认值 NON")
    void shouldUseDefaultLexicalTagWhenMissing() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(LexicalTag.NON, result.getLexicalTag());
    }
  }

  // ========== 属性默认值测试 ==========

  @Nested
  @DisplayName("属性默认值")
  class AttributeDefaults {

    @Test
    @DisplayName("属性缺失时应使用合理默认值")
    void shouldUseDefaultsWhenAttributesMissing() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertFalse(result.isRecordPreferred());
      assertFalse(result.isConceptPreferred());
      assertFalse(result.isPermutedTerm());
      // PrintFlagYN 默认为 true（如果不存在）
      assertTrue(result.isPrintFlag());
    }

    @Test
    @DisplayName("PrintFlagYN=N 应设置 isPrintFlag 为 false")
    void shouldSetPrintFlagFalseWhenN() throws Exception {
      var xml =
          """
          <Term PrintFlagYN="N">
            <String>Term Name</String>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertFalse(result.isPrintFlag());
    }
  }

  // ========== 可选字段测试 ==========

  @Nested
  @DisplayName("可选字段")
  class OptionalFields {

    @Test
    @DisplayName("应解析 ThesaurusIDlist")
    void shouldParseThesaurusIdList() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
            <ThesaurusIDlist>
              <ThesaurusID>FDA SRS (2014)</ThesaurusID>
              <ThesaurusID>NLM (1975)</ThesaurusID>
            </ThesaurusIDlist>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getThesaurusIds()).containsExactly("FDA SRS (2014)", "NLM (1975)");
    }

    @Test
    @DisplayName("应解析 DateCreated")
    void shouldParseDateCreated() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
            <DateCreated>
              <Year>2020</Year>
              <Month>1</Month>
              <Day>15</Day>
            </DateCreated>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(LocalDate.of(2020, 1, 15), result.getDateCreated());
    }

    @Test
    @DisplayName("应解析 EntryVersion")
    void shouldParseEntryVersion() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
            <EntryVersion>ASPIRIN</EntryVersion>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("ASPIRIN", result.getEntryVersion());
    }

    @Test
    @DisplayName("应解析 Abbreviation")
    void shouldParseAbbreviation() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
            <Abbreviation>TN</Abbreviation>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("TN", result.getAbbreviation());
    }

    @Test
    @DisplayName("应解析 SortVersion")
    void shouldParseSortVersion() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
            <SortVersion>TERMNAME</SortVersion>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("TERMNAME", result.getSortVersion());
    }

    @Test
    @DisplayName("应解析 TermNote")
    void shouldParseTermNote() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
            <TermNote>Some note about the term</TermNote>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Some note about the term", result.getTermNote());
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @Test
    @DisplayName("缺少术语文本时应返回 null")
    void shouldReturnNullWhenMissingTermText() throws Exception {
      var xml =
          """
          <Term>
            <TermUI>T000001</TermUI>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("无 TermUI 时仍应成功解析")
    void shouldParseWithoutTermUI() throws Exception {
      var xml =
          """
          <Term>
            <String>Term Name</String>
          </Term>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshEntryTerm result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertNull(result.getTermUi());
      assertEquals("Term Name", result.getTerm());
    }
  }

  // ========== 辅助方法 ==========

  /// 创建定位到起始元素的 XMLStreamReader。
  private XMLStreamReader createReaderAtStartElement(String xml) throws XMLStreamException {
    var reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamConstants.START_ELEMENT) {
        break;
      }
    }
    return reader;
  }
}
