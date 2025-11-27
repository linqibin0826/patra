package com.patra.catalog.infra.adapter.parser.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.infra.adapter.parser.MeshXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ConceptParsingStrategy 单元测试。
///
/// 验证概念解析策略的正确性。
@DisplayName("ConceptParsingStrategy 策略")
class ConceptParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private final ConceptParsingStrategy strategy = ConceptParsingStrategy.INSTANCE;

  // ========== 策略契约测试 ==========

  @Nested
  @DisplayName("策略契约")
  class StrategyContract {

    @Test
    @DisplayName("rootElementName() 应返回 Concept")
    void rootElementName_shouldReturnConcept() {
      assertEquals(MeshXmlElements.Record.CONCEPT, strategy.rootElementName());
    }

    @Test
    @DisplayName("INSTANCE 应为非空单例")
    void instance_shouldBeNonNullSingleton() {
      assertNotNull(ConceptParsingStrategy.INSTANCE);
      assertEquals(ConceptParsingStrategy.INSTANCE, strategy);
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
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("M0000001", result.getConceptUi().ui());
      assertEquals("Aspirin", result.getConceptName());
      assertTrue(result.isPreferred());
    }

    @Test
    @DisplayName("应正确解析 PreferredConceptYN=N")
    void shouldParsePreferredConceptYNFalse() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="N">
            <ConceptUI>M0000002</ConceptUI>
            <ConceptName>
              <String>Acetylsalicylic Acid</String>
            </ConceptName>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertFalse(result.isPreferred());
    }

    @Test
    @DisplayName("PreferredConceptYN 缺失时应默认为 false")
    void shouldDefaultPreferredToFalse() throws Exception {
      var xml =
          """
          <Concept>
            <ConceptUI>M0000003</ConceptUI>
            <ConceptName>
              <String>Some Concept</String>
            </ConceptName>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertFalse(result.isPreferred());
    }
  }

  // ========== 可选文本字段测试 ==========

  @Nested
  @DisplayName("可选文本字段")
  class OptionalTextFields {

    @Test
    @DisplayName("应解析 ScopeNote")
    void shouldParseScopeNote() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
            <ScopeNote>A widely used analgesic and antipyretic.</ScopeNote>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("A widely used analgesic and antipyretic.", result.getScopeNote());
    }

    @Test
    @DisplayName("应解析 CASN1Name")
    void shouldParseCasn1Name() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
            <CASN1Name>2-(Acetyloxy)benzoic Acid</CASN1Name>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("2-(Acetyloxy)benzoic Acid", result.getCasn1Name());
    }

    @Test
    @DisplayName("应解析 ConceptStatus")
    void shouldParseConceptStatus() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
            <ConceptStatus>Active</ConceptStatus>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Active", result.getConceptStatus());
    }

    @Test
    @DisplayName("应解析 TranslatorsEnglishScopeNote")
    void shouldParseTranslatorsEnglishScopeNote() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
            <TranslatorsEnglishScopeNote>English note for translators</TranslatorsEnglishScopeNote>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("English note for translators", result.getTranslatorsEnglishScopeNote());
    }

    @Test
    @DisplayName("应解析 TranslatorsScopeNote")
    void shouldParseTranslatorsScopeNote() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
            <TranslatorsScopeNote>Scope note for translators</TranslatorsScopeNote>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Scope note for translators", result.getTranslatorsScopeNote());
    }
  }

  // ========== 注册号测试 ==========

  @Nested
  @DisplayName("注册号解析")
  class RegistryNumbers {

    @Test
    @DisplayName("应解析单个 RegistryNumber（旧版 DTD）")
    void shouldParseSingleRegistryNumber() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
            <RegistryNumber>50-78-2</RegistryNumber>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getRegistryNumbers()).containsExactly("50-78-2");
    }

    @Test
    @DisplayName("应解析 RegistryNumberList（2025 DTD）")
    void shouldParseRegistryNumberList() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
            <RegistryNumberList>
              <RegistryNumber>50-78-2</RegistryNumber>
              <RegistryNumber>8031-02-7</RegistryNumber>
            </RegistryNumberList>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getRegistryNumbers()).containsExactly("50-78-2", "8031-02-7");
    }

    @Test
    @DisplayName("应解析 RelatedRegistryNumberList")
    void shouldParseRelatedRegistryNumberList() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Calcimycin</String>
            </ConceptName>
            <RelatedRegistryNumberList>
              <RelatedRegistryNumber>52665-69-7 (Calcimycin)</RelatedRegistryNumber>
              <RelatedRegistryNumber>37H9VM9WZL (Calcimycin)</RelatedRegistryNumber>
            </RelatedRegistryNumberList>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getRelatedRegistryNumbers())
          .containsExactly("52665-69-7 (Calcimycin)", "37H9VM9WZL (Calcimycin)");
    }
  }

  // ========== 概念关系测试 ==========

  @Nested
  @DisplayName("概念关系解析")
  class ConceptRelations {

    @Test
    @DisplayName("应解析 ConceptRelationList")
    void shouldParseConceptRelationList() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Calcimycin</String>
            </ConceptName>
            <ConceptRelationList>
              <ConceptRelation RelationName="NRW">
                <Concept1UI>M0000001</Concept1UI>
                <Concept2UI>M0353609</Concept2UI>
              </ConceptRelation>
            </ConceptRelationList>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getConceptRelations()).hasSize(1);
      ConceptRelation relation = result.getConceptRelations().get(0);
      assertEquals("NRW", relation.relationName());
      assertEquals("M0000001", relation.concept1Ui().ui());
      assertEquals("M0353609", relation.concept2Ui().ui());
    }

    @Test
    @DisplayName("应解析多个概念关系")
    void shouldParseMultipleConceptRelations() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Calcimycin</String>
            </ConceptName>
            <ConceptRelationList>
              <ConceptRelation RelationName="NRW">
                <Concept1UI>M0000001</Concept1UI>
                <Concept2UI>M0353609</Concept2UI>
              </ConceptRelation>
              <ConceptRelation RelationName="BRD">
                <Concept1UI>M0000001</Concept1UI>
                <Concept2UI>M0353610</Concept2UI>
              </ConceptRelation>
            </ConceptRelationList>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getConceptRelations()).hasSize(2);
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @Test
    @DisplayName("缺少 ConceptUI 时应返回 null")
    void shouldReturnNullWhenMissingConceptUI() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptName>
              <String>Aspirin</String>
            </ConceptName>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("缺少 ConceptName 时应返回 null")
    void shouldReturnNullWhenMissingConceptName() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("应解析完整的复杂概念")
    void shouldParseComplexConcept() throws Exception {
      var xml =
          """
          <Concept PreferredConceptYN="Y">
            <ConceptUI>M0000001</ConceptUI>
            <ConceptName>
              <String>Calcimycin</String>
            </ConceptName>
            <ScopeNote>An ionophore antibiotic</ScopeNote>
            <CASN1Name>4-Benzoxazolecarboxylic acid</CASN1Name>
            <RegistryNumber>52665-69-7</RegistryNumber>
            <ConceptStatus>Active</ConceptStatus>
            <RelatedRegistryNumberList>
              <RelatedRegistryNumber>37H9VM9WZL</RelatedRegistryNumber>
            </RelatedRegistryNumberList>
            <ConceptRelationList>
              <ConceptRelation RelationName="NRW">
                <Concept1UI>M0000001</Concept1UI>
                <Concept2UI>M0353609</Concept2UI>
              </ConceptRelation>
            </ConceptRelationList>
          </Concept>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshConcept result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("M0000001", result.getConceptUi().ui());
      assertEquals("Calcimycin", result.getConceptName());
      assertTrue(result.isPreferred());
      assertEquals("An ionophore antibiotic", result.getScopeNote());
      assertEquals("4-Benzoxazolecarboxylic acid", result.getCasn1Name());
      assertThat(result.getRegistryNumbers()).containsExactly("52665-69-7");
      assertEquals("Active", result.getConceptStatus());
      assertThat(result.getRelatedRegistryNumbers()).containsExactly("37H9VM9WZL");
      assertThat(result.getConceptRelations()).hasSize(1);
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
