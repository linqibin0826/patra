package dev.linqibin.patra.catalog.infra.parser.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import dev.linqibin.patra.catalog.domain.model.enums.ScrClass;
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

/// ScrParsingStrategy 单元测试。
///
/// 验证补充概念记录（SCR）解析策略的正确性。
@DisplayName("ScrParsingStrategy 策略")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class ScrParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private final ScrParsingStrategy strategy = ScrParsingStrategy.INSTANCE;

  // ========== 策略契约测试 ==========

  @Nested
  @DisplayName("策略契约")
  class StrategyContract {

    @Test
    @DisplayName("rootElementName() 应返回 SupplementalRecord")
    void rootElementName_shouldReturnSupplementalRecord() {
      assertEquals(MeshXmlElements.Record.SUPPLEMENTAL, strategy.rootElementName());
    }

    @Test
    @DisplayName("INSTANCE 应为非空单例")
    void instance_shouldBeNonNullSingleton() {
      assertNotNull(ScrParsingStrategy.INSTANCE);
      assertEquals(ScrParsingStrategy.INSTANCE, strategy);
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
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Aspirin-Caffeine-Salicylamide</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("C000001", result.getUi().ui());
      assertEquals("Aspirin-Caffeine-Salicylamide", result.getName());
      assertEquals(ScrClass.CHEMICAL, result.getScrClass());
      // meshVersion 由调用方设置，Strategy 不再从 context 获取
      assertNull(result.getMeshVersion());
      assertTrue(result.isActive());
    }

    @Test
    @DisplayName("应正确解析不同的 SCRClass 值 - 化学物质(1)")
    void shouldParseScrClassChemical() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000002</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Chemical Compound</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(ScrClass.CHEMICAL, result.getScrClass());
    }

    @Test
    @DisplayName("应正确解析不同的 SCRClass 值 - 化疗方案(2)")
    void shouldParseScrClassProtocol() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="2">
            <SupplementalRecordUI>C000003</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>CHOP Protocol</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(ScrClass.PROTOCOL, result.getScrClass());
    }

    @Test
    @DisplayName("应正确解析不同的 SCRClass 值 - 疾病(3)")
    void shouldParseScrClassDisease() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="3">
            <SupplementalRecordUI>C000004</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>COVID-19</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(ScrClass.DISEASE, result.getScrClass());
    }

    @Test
    @DisplayName("应正确解析不同的 SCRClass 值 - 生物体(4)")
    void shouldParseScrClassOrganism() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="4">
            <SupplementalRecordUI>C000005</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>SARS-CoV-2</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(ScrClass.ORGANISM, result.getScrClass());
    }

    @Test
    @DisplayName("应正确解析不同的 SCRClass 值 - 人群组(5)")
    void shouldParseScrClassPopulationGroup() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="5">
            <SupplementalRecordUI>C000006</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Han Chinese</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(ScrClass.POPULATION_GROUP, result.getScrClass());
    }

    @Test
    @DisplayName("应正确解析不同的 SCRClass 值 - 解剖结构(6)")
    void shouldParseScrClassAnatomy() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="6">
            <SupplementalRecordUI>C000007</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Rhomboid Fossa</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(ScrClass.ANATOMY, result.getScrClass());
    }

    @Test
    @DisplayName("SCRClass 缺失时应使用默认值 CHEMICAL")
    void shouldDefaultScrClassToChemical() throws Exception {
      var xml =
          """
          <SupplementalRecord>
            <SupplementalRecordUI>C000008</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(ScrClass.CHEMICAL, result.getScrClass());
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
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <DateCreated>
              <Year>1995</Year>
              <Month>06</Month>
              <Day>15</Day>
            </DateCreated>
            <DateRevised>
              <Year>2023</Year>
              <Month>12</Month>
              <Day>31</Day>
            </DateRevised>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(LocalDate.of(1995, 6, 15), result.getDateCreated());
      assertEquals(LocalDate.of(2023, 12, 31), result.getDateRevised());
    }
  }

  // ========== 文本字段测试 ==========

  @Nested
  @DisplayName("文本字段")
  class TextFields {

    @Test
    @DisplayName("应解析 Note 字段")
    void shouldParseNoteField() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <Note>A calcium ionophore. Used to study calcium transport.</Note>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("A calcium ionophore. Used to study calcium transport.", result.getNote());
    }

    @Test
    @DisplayName("应解析 Frequency 字段")
    void shouldParseFrequencyField() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <Frequency>5</Frequency>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("5", result.getFrequency());
    }
  }

  // ========== PreviousIndexingList 测试 ==========

  @Nested
  @DisplayName("PreviousIndexingList")
  class PreviousIndexingListParsing {

    @Test
    @DisplayName("应解析 PreviousIndexingList 并合并为换行分隔的字符串")
    void shouldParsePreviousIndexingList() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <PreviousIndexingList>
              <PreviousIndexing>Antibiotics (1973-1974)</PreviousIndexing>
              <PreviousIndexing>Ionophores (1975-1990)</PreviousIndexing>
            </PreviousIndexingList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Antibiotics (1973-1974)\nIonophores (1975-1990)", result.getPreviousIndexing());
    }
  }

  // ========== HeadingMappedToList 测试 ==========

  @Nested
  @DisplayName("HeadingMappedToList")
  class HeadingMappedToListParsing {

    @Test
    @DisplayName("应解析只有主题词的 HeadingMappedTo")
    void shouldParseHeadingMappedToWithDescriptorOnly() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <HeadingMappedToList>
              <HeadingMappedTo>
                <DescriptorReferredTo>
                  <DescriptorUI>D000001</DescriptorUI>
                  <DescriptorName>
                    <String>Calcimycin</String>
                  </DescriptorName>
                </DescriptorReferredTo>
              </HeadingMappedTo>
            </HeadingMappedToList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getHeadingMappedTos()).hasSize(1);
      assertEquals("D000001", result.getHeadingMappedTos().get(0).descriptorUi().ui());
      assertNull(result.getHeadingMappedTos().get(0).qualifierUi());
    }

    @Test
    @DisplayName("应解析带限定词的 HeadingMappedTo")
    void shouldParseHeadingMappedToWithQualifier() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <HeadingMappedToList>
              <HeadingMappedTo>
                <DescriptorReferredTo>
                  <DescriptorUI>D000002</DescriptorUI>
                  <DescriptorName>
                    <String>Test Descriptor</String>
                  </DescriptorName>
                </DescriptorReferredTo>
                <QualifierReferredTo>
                  <QualifierUI>Q000008</QualifierUI>
                  <QualifierName>
                    <String>administration &amp; dosage</String>
                  </QualifierName>
                </QualifierReferredTo>
              </HeadingMappedTo>
            </HeadingMappedToList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getHeadingMappedTos()).hasSize(1);
      var mapping = result.getHeadingMappedTos().get(0);
      assertEquals("D000002", mapping.descriptorUi().ui());
      assertEquals("Q000008", mapping.qualifierUi().ui());
    }

    @Test
    @DisplayName("应解析多个 HeadingMappedTo")
    void shouldParseMultipleHeadingMappedTos() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <HeadingMappedToList>
              <HeadingMappedTo>
                <DescriptorReferredTo>
                  <DescriptorUI>D000001</DescriptorUI>
                  <DescriptorName>
                    <String>First Descriptor</String>
                  </DescriptorName>
                </DescriptorReferredTo>
              </HeadingMappedTo>
              <HeadingMappedTo>
                <DescriptorReferredTo>
                  <DescriptorUI>D000002</DescriptorUI>
                  <DescriptorName>
                    <String>Second Descriptor</String>
                  </DescriptorName>
                </DescriptorReferredTo>
              </HeadingMappedTo>
            </HeadingMappedToList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getHeadingMappedTos()).hasSize(2);
      assertEquals("D000001", result.getHeadingMappedTos().get(0).descriptorUi().ui());
      assertEquals("D000002", result.getHeadingMappedTos().get(1).descriptorUi().ui());
    }

    @Test
    @DisplayName("应解析带星号前缀的 HeadingMappedTo（Major Topic）")
    void shouldParseHeadingMappedToWithAsteriskPrefix() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <HeadingMappedToList>
              <HeadingMappedTo>
                <DescriptorReferredTo>
                  <DescriptorUI>*D000001</DescriptorUI>
                  <DescriptorName>
                    <String>Major Topic Descriptor</String>
                  </DescriptorName>
                </DescriptorReferredTo>
              </HeadingMappedTo>
              <HeadingMappedTo>
                <DescriptorReferredTo>
                  <DescriptorUI>D000002</DescriptorUI>
                  <DescriptorName>
                    <String>Non-Major Descriptor</String>
                  </DescriptorName>
                </DescriptorReferredTo>
              </HeadingMappedTo>
            </HeadingMappedToList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getHeadingMappedTos()).hasSize(2);
      // 第一个带星号，应该是 majorTopic=true，UI 应该剥离星号
      var majorMapping = result.getHeadingMappedTos().get(0);
      assertEquals("D000001", majorMapping.descriptorUi().ui());
      assertTrue(majorMapping.majorTopic(), "带星号的映射应该是 majorTopic");
      // 第二个不带星号，应该是 majorTopic=false
      var normalMapping = result.getHeadingMappedTos().get(1);
      assertEquals("D000002", normalMapping.descriptorUi().ui());
      assertFalse(normalMapping.majorTopic(), "不带星号的映射不应该是 majorTopic");
    }
  }

  // ========== SourceList 测试 ==========

  @Nested
  @DisplayName("SourceList")
  class SourceListParsing {

    @Test
    @DisplayName("应解析 SourceList")
    void shouldParseSourceList() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <SourceList>
              <Source>NCI2004_11_17</Source>
              <Source>FDA SRS (2023)</Source>
            </SourceList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getSources()).hasSize(2);
      assertEquals("NCI2004_11_17", result.getSources().get(0).source());
      assertEquals("FDA SRS (2023)", result.getSources().get(1).source());
    }
  }

  // ========== IndexingInformationList 测试 ==========

  @Nested
  @DisplayName("IndexingInformationList")
  class IndexingInformationListParsing {

    @Test
    @DisplayName("应解析只有主题词的 IndexingInformation")
    void shouldParseIndexingInfoWithDescriptorOnly() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <IndexingInformationList>
              <IndexingInformation>
                <DescriptorReferredTo>
                  <DescriptorUI>D000001</DescriptorUI>
                  <DescriptorName>
                    <String>Test Descriptor</String>
                  </DescriptorName>
                </DescriptorReferredTo>
              </IndexingInformation>
            </IndexingInformationList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getIndexingInfos()).hasSize(1);
      assertEquals("D000001", result.getIndexingInfos().get(0).descriptorUi().ui());
      assertNull(result.getIndexingInfos().get(0).qualifierUi());
      assertNull(result.getIndexingInfos().get(0).chemicalUi());
    }

    @Test
    @DisplayName("应解析带限定词的 IndexingInformation")
    void shouldParseIndexingInfoWithQualifier() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <IndexingInformationList>
              <IndexingInformation>
                <DescriptorReferredTo>
                  <DescriptorUI>D000001</DescriptorUI>
                  <DescriptorName>
                    <String>Test Descriptor</String>
                  </DescriptorName>
                </DescriptorReferredTo>
                <QualifierReferredTo>
                  <QualifierUI>Q000009</QualifierUI>
                  <QualifierName>
                    <String>adverse effects</String>
                  </QualifierName>
                </QualifierReferredTo>
              </IndexingInformation>
            </IndexingInformationList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getIndexingInfos()).hasSize(1);
      var info = result.getIndexingInfos().get(0);
      assertEquals("D000001", info.descriptorUi().ui());
      assertEquals("Q000009", info.qualifierUi().ui());
    }

    @Test
    @DisplayName("应解析引用其他 SCR 的 IndexingInformation")
    void shouldParseIndexingInfoWithChemicalReference() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <IndexingInformationList>
              <IndexingInformation>
                <SupplementalRecordReferredTo>
                  <SupplementalRecordUI>C000099</SupplementalRecordUI>
                  <SupplementalRecordName>
                    <String>Related Chemical</String>
                  </SupplementalRecordName>
                </SupplementalRecordReferredTo>
              </IndexingInformation>
            </IndexingInformationList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getIndexingInfos()).hasSize(1);
      var info = result.getIndexingInfos().get(0);
      assertNull(info.descriptorUi());
      assertNull(info.qualifierUi());
      assertEquals("C000099", info.chemicalUi().ui());
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
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
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
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getPharmacologicalActions()).hasSize(1);
      assertEquals("D000890", result.getPharmacologicalActions().get(0).descriptorUi().ui());
      assertEquals(
          "Anti-Infective Agents", result.getPharmacologicalActions().get(0).descriptorName());
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
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
            <ConceptList>
              <Concept PreferredConceptYN="Y">
                <ConceptUI>M0000001</ConceptUI>
                <ConceptName>
                  <String>Test Concept</String>
                </ConceptName>
                <ScopeNote>A test concept for SCR</ScopeNote>
              </Concept>
              <Concept PreferredConceptYN="N">
                <ConceptUI>M0000002</ConceptUI>
                <ConceptName>
                  <String>Secondary Concept</String>
                </ConceptName>
              </Concept>
            </ConceptList>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getConcepts()).hasSize(2);
      assertTrue(result.getConcepts().get(0).isPreferred());
      assertEquals("M0000001", result.getConcepts().get(0).getConceptUi().ui());
      assertEquals("A test concept for SCR", result.getConcepts().get(0).getScopeNote());
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @Test
    @DisplayName("缺少 SupplementalRecordUI 时应返回 null")
    void shouldReturnNullWhenMissingScrUI() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordName>
              <String>Test</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("缺少 SupplementalRecordName 时应返回 null")
    void shouldReturnNullWhenMissingScrName() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("无上下文时 meshVersion 应为 null")
    void shouldHaveNullMeshVersionWithEmptyContext() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="1">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertNull(result.getMeshVersion());
    }

    @Test
    @DisplayName("无效 SCRClass 值时应使用默认值 CHEMICAL")
    void shouldDefaultToChemicalForInvalidScrClass() throws Exception {
      var xml =
          """
          <SupplementalRecord SCRClass="99">
            <SupplementalRecordUI>C000001</SupplementalRecordUI>
            <SupplementalRecordName>
              <String>Test SCR</String>
            </SupplementalRecordName>
          </SupplementalRecord>
          """;
      var reader = createReaderAtStartElement(xml);

      MeshScrAggregate result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals(ScrClass.CHEMICAL, result.getScrClass());
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
