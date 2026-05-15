package dev.linqibin.patra.catalog.infra.parser.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import dev.linqibin.patra.catalog.infra.parser.MeshXmlElements;
import dev.linqibin.patra.catalog.infra.parser.support.XmlParsingContext;
import java.io.StringReader;
import java.time.LocalDate;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// QualifierParsingStrategy 单元测试。
///
/// 验证限定词解析策略的正确性。
@DisplayName("QualifierParsingStrategy 策略")
class QualifierParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private final QualifierParsingStrategy strategy = QualifierParsingStrategy.INSTANCE;

  // ========== 策略契约测试 ==========

  @Nested
  @DisplayName("策略契约")
  class StrategyContract {

    @Test
    @DisplayName("rootElementName() 应返回 QualifierRecord")
    void rootElementName_shouldReturnQualifierRecord() {
      assertEquals(MeshXmlElements.Record.QUALIFIER, strategy.rootElementName());
    }

    @Test
    @DisplayName("INSTANCE 应为非空单例")
    void instance_shouldBeNonNullSingleton() {
      assertNotNull(QualifierParsingStrategy.INSTANCE);
      assertEquals(QualifierParsingStrategy.INSTANCE, strategy);
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
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <ConceptUI>M0030185</ConceptUI>
                <TermList>
                  <Term RecordPreferredTermYN="Y" ConceptPreferredTermYN="Y">
                    <TermUI>T000003</TermUI>
                    <String>diagnosis</String>
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("Q000001", result.getQualifierUi().ui());
      assertEquals("diagnosis", result.getName());
      assertEquals("DI", result.getAbbreviation());
      // meshVersion 由调用方设置，Strategy 不再从 context 获取
      assertNull(result.getMeshVersion());
      assertTrue(result.isActive());
    }

    @Test
    @DisplayName("应正确解析 10 位格式的 QualifierUI")
    void shouldParse10DigitQualifierUI() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000000981</QualifierUI>
            <QualifierName>
              <String>diagnostic imaging</String>
            </QualifierName>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DG</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("Q000000981", result.getQualifierUi().ui());
    }
  }

  // ========== 可选字段测试 ==========

  @Nested
  @DisplayName("可选字段")
  class OptionalFields {

    @Test
    @DisplayName("应解析 Annotation")
    void shouldParseAnnotation() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <Annotation>Used with diseases for diagnostic procedures</Annotation>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Used with diseases for diagnostic procedures", result.getAnnotation());
    }

    @Test
    @DisplayName("应解析日期字段")
    void shouldParseDateFields() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <DateCreated>
              <Year>1966</Year>
              <Month>01</Month>
              <Day>01</Day>
            </DateCreated>
            <DateRevised>
              <Year>2020</Year>
              <Month>06</Month>
              <Day>15</Day>
            </DateRevised>
            <DateEstablished>
              <Year>1966</Year>
              <Month>01</Month>
              <Day>01</Day>
            </DateEstablished>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(LocalDate.of(1966, 1, 1), result.getDateCreated());
      assertEquals(LocalDate.of(2020, 6, 15), result.getDateRevised());
      assertEquals(LocalDate.of(1966, 1, 1), result.getDateEstablished());
    }

    @Test
    @DisplayName("应解析 HistoryNote 和 OnlineNote")
    void shouldParseNotes() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <HistoryNote>66; used with Category A-D 1966-74</HistoryNote>
            <OnlineNote>search policy: Online Manual</OnlineNote>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("66; used with Category A-D 1966-74", result.getHistoryNote());
      assertEquals("search policy: Online Manual", result.getOnlineNote());
    }

    @Test
    @DisplayName("应解析 TreeNumberList")
    void shouldParseTreeNumberList() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <TreeNumberList>
              <TreeNumber>Y01.060</TreeNumber>
              <TreeNumber>Y02.060</TreeNumber>
            </TreeNumberList>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getTreeNumbers()).containsExactly("Y01.060", "Y02.060");
    }
  }

  // ========== Abbreviation 提取测试 ==========

  @Nested
  @DisplayName("Abbreviation 提取")
  class AbbreviationExtraction {

    @Test
    @DisplayName("应从首选概念的首选术语中提取 Abbreviation")
    void shouldExtractAbbreviationFromPreferredTerm() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <ConceptList>
              <Concept PreferredConceptYN="N">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>WRONG</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="N">
                    <Abbreviation>ALSO_WRONG</Abbreviation>
                  </Term>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("DI", result.getAbbreviation());
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @Test
    @DisplayName("缺少 QualifierUI 时应返回 null")
    void shouldReturnNullWhenMissingQualifierUI() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("缺少 QualifierName 时应返回 null")
    void shouldReturnNullWhenMissingQualifierName() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("缺少 Abbreviation 时应返回 null")
    void shouldReturnNullWhenMissingAbbreviation() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <String>diagnosis</String>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("无上下文时 meshVersion 应为 null")
    void shouldHaveNullMeshVersionWithEmptyContext() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <TermList>
                  <Term RecordPreferredTermYN="Y">
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertNull(result.getMeshVersion());
    }

    @Test
    @DisplayName("应解析完整的复杂限定词")
    void shouldParseComplexQualifier() throws Exception {
      var xml =
          """
          <QualifierRecord>
            <QualifierUI>Q000001</QualifierUI>
            <QualifierName>
              <String>diagnosis</String>
            </QualifierName>
            <Annotation>Used with diseases for diagnostic procedures</Annotation>
            <DateCreated>
              <Year>1966</Year>
              <Month>01</Month>
              <Day>01</Day>
            </DateCreated>
            <DateRevised>
              <Year>2020</Year>
              <Month>06</Month>
              <Day>15</Day>
            </DateRevised>
            <DateEstablished>
              <Year>1966</Year>
              <Month>01</Month>
              <Day>01</Day>
            </DateEstablished>
            <HistoryNote>66; used with Category A-D 1966-74</HistoryNote>
            <OnlineNote>search policy: Online Manual</OnlineNote>
            <TreeNumberList>
              <TreeNumber>Y01.060</TreeNumber>
            </TreeNumberList>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <ConceptUI>M0030185</ConceptUI>
                <TermList>
                  <Term RecordPreferredTermYN="Y" ConceptPreferredTermYN="Y">
                    <TermUI>T000003</TermUI>
                    <String>diagnosis</String>
                    <Abbreviation>DI</Abbreviation>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </QualifierRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshQualifierAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("Q000001", result.getQualifierUi().ui());
      assertEquals("diagnosis", result.getName());
      assertEquals("DI", result.getAbbreviation());
      assertEquals("Used with diseases for diagnostic procedures", result.getAnnotation());
      assertEquals(LocalDate.of(1966, 1, 1), result.getDateCreated());
      assertEquals(LocalDate.of(2020, 6, 15), result.getDateRevised());
      assertEquals(LocalDate.of(1966, 1, 1), result.getDateEstablished());
      assertEquals("66; used with Category A-D 1966-74", result.getHistoryNote());
      assertEquals("search policy: Online Manual", result.getOnlineNote());
      assertThat(result.getTreeNumbers()).containsExactly("Y01.060");
      // meshVersion 由调用方设置，Strategy 不再从 context 获取
      assertNull(result.getMeshVersion());
      assertTrue(result.isActive());
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
