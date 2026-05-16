package dev.linqibin.patra.catalog.infra.parser.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import dev.linqibin.patra.catalog.domain.model.enums.DescriptorClass;
import dev.linqibin.patra.catalog.infra.parser.MeshXmlElements;
import dev.linqibin.patra.catalog.infra.parser.support.XmlParsingContext;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// DescriptorParsingStrategy 单元测试。
///
/// 验证主题词解析策略的正确性。
@DisplayName("DescriptorParsingStrategy 策略")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DescriptorParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private final DescriptorParsingStrategy strategy = DescriptorParsingStrategy.INSTANCE;

  // ========== 策略契约测试 ==========

  @Nested
  @DisplayName("策略契约")
  class StrategyContract {

    @Test
    @DisplayName("rootElementName() 应返回 DescriptorRecord")
    void rootElementName_shouldReturnDescriptorRecord() {
      assertEquals(MeshXmlElements.Record.DESCRIPTOR, strategy.rootElementName());
    }

    @Test
    @DisplayName("INSTANCE 应为非空单例")
    void instance_shouldBeNonNullSingleton() {
      assertNotNull(DescriptorParsingStrategy.INSTANCE);
      assertEquals(DescriptorParsingStrategy.INSTANCE, strategy);
    }
  }

  // ========== 基本字段测试 ==========

  @Nested
  @DisplayName("parseRecord() 基本字段")
  class ParseRecordBasicFields {

    @Test
    @DisplayName("应提取所有必填字段")
    void shouldExtractAllRequiredFields() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("D000001", result.getUi().ui());
      assertEquals("Calcimycin", result.getName());
      assertEquals(DescriptorClass.TOPICAL, result.getDescriptorClass());
      // meshVersion 由调用方设置，Strategy 不再从 context 获取
      assertNull(result.getMeshVersion());
      assertTrue(result.isActive());
    }

    @Test
    @DisplayName("应正确解析不同的 DescriptorClass 值")
    void shouldParseDescriptorClass() throws Exception {
      // 测试出版类型
      var xml =
          """
          <DescriptorRecord DescriptorClass="2">
            <DescriptorUI>D000002</DescriptorUI>
            <DescriptorName>
              <String>Review</String>
            </DescriptorName>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(DescriptorClass.PUBLICATION_TYPE, result.getDescriptorClass());
    }

    @Test
    @DisplayName("DescriptorClass 缺失时应使用默认值 TOPICAL")
    void shouldDefaultDescriptorClassToTopical() throws Exception {
      var xml =
          """
          <DescriptorRecord>
            <DescriptorUI>D000003</DescriptorUI>
            <DescriptorName>
              <String>Test</String>
            </DescriptorName>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(DescriptorClass.TOPICAL, result.getDescriptorClass());
    }
  }

  // ========== 日期字段测试 ==========

  @Nested
  @DisplayName("日期字段")
  class DateFields {

    @Test
    @DisplayName("应解析所有日期字段")
    void shouldParseDateFields() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <DateCreated>
              <Year>1974</Year>
              <Month>11</Month>
              <Day>19</Day>
            </DateCreated>
            <DateRevised>
              <Year>2023</Year>
              <Month>06</Month>
              <Day>15</Day>
            </DateRevised>
            <DateEstablished>
              <Year>1984</Year>
              <Month>01</Month>
              <Day>01</Day>
            </DateEstablished>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(LocalDate.of(1974, 11, 19), result.getDateCreated());
      assertEquals(LocalDate.of(2023, 6, 15), result.getDateRevised());
      assertEquals(LocalDate.of(1984, 1, 1), result.getDateEstablished());
    }
  }

  // ========== 文本字段测试 ==========

  @Nested
  @DisplayName("文本字段")
  class TextFields {

    @Test
    @DisplayName("应解析所有文本字段")
    void shouldParseTextFields() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <HistoryNote>91(75)</HistoryNote>
            <OnlineNote>use CALCIMYCIN to search A 23187 1975-90</OnlineNote>
            <PublicMeSHNote>91</PublicMeSHNote>
            <NLMClassificationNumber>QV 38</NLMClassificationNumber>
            <Annotation>note annotation text</Annotation>
            <ConsiderAlso>consider also terms</ConsiderAlso>
            <ScopeNote>An ionophoric antibiotic.</ScopeNote>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("91(75)", result.getHistoryNote());
      assertEquals("use CALCIMYCIN to search A 23187 1975-90", result.getOnlineNote());
      assertEquals("91", result.getPublicMeshNote());
      assertEquals("QV 38", result.getNlmClassificationNumber());
      assertEquals("note annotation text", result.getAnnotation());
      assertEquals("consider also terms", result.getConsiderAlso());
      assertEquals("An ionophoric antibiotic.", result.getScopeNote());
    }
  }

  // ========== TreeNumberList 测试 ==========

  @Nested
  @DisplayName("TreeNumberList")
  class TreeNumberListParsing {

    @Test
    @DisplayName("应解析 TreeNumberList 并标记第一个为 primary")
    void shouldParseTreeNumberListWithPrimary() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <TreeNumberList>
              <TreeNumber>D03.438.221.173</TreeNumber>
              <TreeNumber>D03.633.100.221.173</TreeNumber>
            </TreeNumberList>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getTreeNumbers()).hasSize(2);
      assertTrue(result.getTreeNumbers().get(0).isPrimary());
      assertEquals("D03.438.221.173", result.getTreeNumbers().get(0).getTreeNumber());
      assertEquals("D03.633.100.221.173", result.getTreeNumbers().get(1).getTreeNumber());
    }
  }

  // ========== AllowableQualifiersList 测试 ==========

  @Nested
  @DisplayName("AllowableQualifiersList")
  class AllowableQualifiersListParsing {

    @Test
    @DisplayName("应解析 AllowableQualifiersList")
    void shouldParseAllowableQualifiersList() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <AllowableQualifiersList>
              <AllowableQualifier>
                <QualifierReferredTo>
                  <QualifierUI>Q000008</QualifierUI>
                  <QualifierName>
                    <String>administration &amp; dosage</String>
                  </QualifierName>
                </QualifierReferredTo>
                <Abbreviation>AD</Abbreviation>
              </AllowableQualifier>
              <AllowableQualifier>
                <QualifierReferredTo>
                  <QualifierUI>Q000009</QualifierUI>
                  <QualifierName>
                    <String>adverse effects</String>
                  </QualifierName>
                </QualifierReferredTo>
                <Abbreviation>AE</Abbreviation>
              </AllowableQualifier>
            </AllowableQualifiersList>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAllowableQualifiers()).hasSize(2);
      assertEquals("Q000008", result.getAllowableQualifiers().get(0).qualifierUi().ui());
      assertEquals("AD", result.getAllowableQualifiers().get(0).abbreviation());
    }
  }

  // ========== PharmacologicalActionList 测试 ==========

  @Nested
  @DisplayName("PharmacologicalActionList")
  class PharmacologicalActionListParsing {

    @Test
    @DisplayName("应解析 PharmacologicalActionList")
    void shouldParsePharmacologicalActionList() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <PharmacologicalActionList>
              <PharmacologicalAction>
                <DescriptorReferredTo>
                  <DescriptorUI>D000890</DescriptorUI>
                  <DescriptorName>
                    <String>Anti-Infective Agents</String>
                  </DescriptorName>
                </DescriptorReferredTo>
              </PharmacologicalAction>
            </PharmacologicalActionList>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getPharmacologicalActions()).hasSize(1);
      assertEquals("D000890", result.getPharmacologicalActions().get(0).descriptorUi().ui());
      assertEquals(
          "Anti-Infective Agents", result.getPharmacologicalActions().get(0).descriptorName());
    }
  }

  // ========== PreviousIndexingList 测试 ==========

  @Nested
  @DisplayName("PreviousIndexingList")
  class PreviousIndexingListParsing {

    @Test
    @DisplayName("应解析 PreviousIndexingList")
    void shouldParsePreviousIndexingList() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <PreviousIndexingList>
              <PreviousIndexing>Antibiotics (1973-1974)</PreviousIndexing>
              <PreviousIndexing>Ionophores (1975-1990)</PreviousIndexing>
            </PreviousIndexingList>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getPreviousIndexings())
          .containsExactly("Antibiotics (1973-1974)", "Ionophores (1975-1990)");
    }
  }

  // ========== SeeRelatedList 测试 ==========

  @Nested
  @DisplayName("SeeRelatedList")
  class SeeRelatedListParsing {

    @Test
    @DisplayName("应解析 SeeRelatedList")
    void shouldParseSeeRelatedList() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <SeeRelatedList>
              <SeeRelatedDescriptor>
                <DescriptorReferredTo>
                  <DescriptorUI>D007476</DescriptorUI>
                  <DescriptorName>
                    <String>Ionophores</String>
                  </DescriptorName>
                </DescriptorReferredTo>
              </SeeRelatedDescriptor>
            </SeeRelatedList>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getSeeRelatedDescriptors()).hasSize(1);
      assertEquals("D007476", result.getSeeRelatedDescriptors().get(0).descriptorUi().ui());
      assertEquals("Ionophores", result.getSeeRelatedDescriptors().get(0).descriptorName());
    }
  }

  // ========== EntryCombinationList 测试 ==========

  @Nested
  @DisplayName("EntryCombinationList")
  class EntryCombinationListParsing {

    @Test
    @DisplayName("应解析 EntryCombinationList")
    void shouldParseEntryCombinationList() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <EntryCombinationList>
              <EntryCombination>
                <ECIN>
                  <DescriptorReferredTo>
                    <DescriptorUI>D000001</DescriptorUI>
                    <DescriptorName>
                      <String>Calcimycin</String>
                    </DescriptorName>
                  </DescriptorReferredTo>
                  <QualifierReferredTo>
                    <QualifierUI>Q000627</QualifierUI>
                    <QualifierName>
                      <String>therapeutic use</String>
                    </QualifierName>
                  </QualifierReferredTo>
                </ECIN>
                <ECOUT>
                  <DescriptorReferredTo>
                    <DescriptorUI>D000001</DescriptorUI>
                    <DescriptorName>
                      <String>Calcimycin</String>
                    </DescriptorName>
                  </DescriptorReferredTo>
                  <QualifierReferredTo>
                    <QualifierUI>Q000494</QualifierUI>
                    <QualifierName>
                      <String>pharmacology</String>
                    </QualifierName>
                  </QualifierReferredTo>
                </ECOUT>
              </EntryCombination>
            </EntryCombinationList>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getEntryCombinations()).hasSize(1);
      var combo = result.getEntryCombinations().get(0);
      assertEquals("D000001", combo.ecinDescriptorUi().ui());
      assertEquals("Q000627", combo.ecinQualifierUi().ui());
      assertEquals("D000001", combo.ecoutDescriptorUi().ui());
      assertEquals("Q000494", combo.ecoutQualifierUi().ui());
    }
  }

  // ========== ConceptList 测试 ==========

  @Nested
  @DisplayName("ConceptList")
  class ConceptListParsing {

    @Test
    @DisplayName("应解析 ConceptList 中的 Concept")
    void shouldParseConceptsFromConceptList() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <ConceptUI>M0000001</ConceptUI>
                <ConceptName>
                  <String>Calcimycin</String>
                </ConceptName>
                <ScopeNote>An ionophoric antibiotic</ScopeNote>
              </Concept>
              <Concept PreferredConceptYN="N">
                <ConceptUI>M0353609</ConceptUI>
                <ConceptName>
                  <String>A-23187</String>
                </ConceptName>
              </Concept>
            </ConceptList>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getConcepts()).hasSize(2);
      assertTrue(result.getConcepts().get(0).isPreferred());
      assertEquals("M0000001", result.getConcepts().get(0).getConceptUi().ui());
      assertEquals("An ionophoric antibiotic", result.getConcepts().get(0).getScopeNote());
    }

    @Test
    @DisplayName("应解析 ConceptList 中的 EntryTerm")
    void shouldParseEntryTermsFromConceptList() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <ConceptUI>M0000001</ConceptUI>
                <ConceptName>
                  <String>Calcimycin</String>
                </ConceptName>
                <TermList>
                  <Term RecordPreferredTermYN="Y" ConceptPreferredTermYN="Y" LexicalTag="NON">
                    <TermUI>T000001</TermUI>
                    <String>Calcimycin</String>
                  </Term>
                  <Term RecordPreferredTermYN="N" ConceptPreferredTermYN="N" LexicalTag="NON">
                    <TermUI>T000002</TermUI>
                    <String>A23187</String>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getEntryTerms()).hasSize(2);
      assertTrue(result.getEntryTerms().get(0).isRecordPreferred());
      assertEquals("T000001", result.getEntryTerms().get(0).getTermUi().ui());
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @Test
    @DisplayName("缺少 DescriptorUI 时应返回 null")
    void shouldReturnNullWhenMissingDescriptorUI() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorName>
              <String>Test</String>
            </DescriptorName>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("缺少 DescriptorName 时应返回 null")
    void shouldReturnNullWhenMissingDescriptorName() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("无上下文时 meshVersion 应为 null")
    void shouldHaveNullMeshVersionWithEmptyContext() throws Exception {
      var xml =
          """
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
          </DescriptorRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshDescriptorAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertNull(result.getMeshVersion());
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
