package com.patra.catalog.infra.adapter.parser.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.dto.serfile.SerialIndexingHistory;
import com.patra.catalog.domain.model.dto.serfile.SerialMeshHeading;
import com.patra.catalog.domain.model.dto.serfile.SerialRecord;
import com.patra.catalog.domain.model.dto.serfile.SerialTitleRelated;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// SerialParsingStrategy 单元测试。
///
/// 测试 NLM Serfile XML 解析策略的正确性。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SerialParsingStrategy 单元测试")
@Timeout(2)
class SerialParsingStrategyTest {

  private SerialParsingStrategy strategy;
  private XmlParsingContext context;

  @BeforeEach
  void setUp() {
    strategy = SerialParsingStrategy.INSTANCE;
    context = XmlParsingContext.empty();
  }

  @Nested
  @DisplayName("rootElementName 测试")
  class RootElementNameTest {

    @Test
    @DisplayName("应返回 Serial 作为根元素名称")
    void shouldReturnSerialAsRootElementName() {
      assertThat(strategy.rootElementName()).isEqualTo("Serial");
    }
  }

  @Nested
  @DisplayName("最小必填字段解析测试")
  class MinimalFieldsTest {

    @Test
    @DisplayName("应正确解析最小必填字段")
    void shouldParseMinimalRequiredFields() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/minimal-serial.xml");

      // Then
      assertThat(record).isNotNull();
      assertThat(record.nlmUniqueId()).isEqualTo("0001234");
      assertThat(record.title()).isEqualTo("Journal of Minimal Test");
    }
  }

  @Nested
  @DisplayName("完整字段解析测试")
  class FullFieldsTest {

    @Test
    @DisplayName("应正确解析所有基本字段")
    void shouldParseAllBasicFields() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then - 基本标识符
      assertThat(record.nlmUniqueId()).isEqualTo("0123456");
      assertThat(record.title()).isEqualTo("Journal of Full Test Medicine");
      assertThat(record.medlineTA()).isEqualTo("J Full Test Med");
      assertThat(record.coden()).isEqualTo("JFTMED");
    }

    @Test
    @DisplayName("应正确解析 ISSN 信息")
    void shouldParseIssnInfo() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then
      assertThat(record.issnL()).isEqualTo("1234-5678");
      assertThat(record.issnPrint()).isEqualTo("1234-5678");
      assertThat(record.issnElectronic()).isEqualTo("1234-5679");
    }

    @Test
    @DisplayName("应正确解析出版信息")
    void shouldParsePublicationInfo() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then
      assertThat(record.country()).isEqualTo("United States");
      assertThat(record.frequency()).isEqualTo("Monthly");
      assertThat(record.publicationFirstYear()).isEqualTo(1990);
      assertThat(record.publicationEndYear()).isEqualTo(2020);
    }

    @Test
    @DisplayName("应正确解析语言列表")
    void shouldParseLanguages() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then
      assertThat(record.languages()).hasSize(2);
      assertThat(record.getPrimaryLanguage()).isEqualTo("eng");
      assertThat(record.languages()).containsExactly("eng", "fre");
    }

    @Test
    @DisplayName("应正确解析 MeSH 主题词列表")
    void shouldParseMeshHeadings() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then
      List<SerialMeshHeading> meshHeadings = record.meshHeadings();
      assertThat(meshHeadings).hasSize(2);

      // 第一个：主要主题，无限定符
      SerialMeshHeading first = meshHeadings.getFirst();
      assertThat(first.descriptorName()).isEqualTo("Cardiology");
      assertThat(first.isMajorTopic()).isTrue();
      assertThat(first.hasQualifier()).isFalse();

      // 第二个：非主要主题，有限定符
      SerialMeshHeading second = meshHeadings.get(1);
      assertThat(second.descriptorName()).isEqualTo("Medicine");
      assertThat(second.isMajorTopic()).isFalse();
      assertThat(second.hasQualifier()).isTrue();
      assertThat(second.qualifierName()).isEqualTo("methods");
    }

    @Test
    @DisplayName("应正确解析期刊关联关系")
    void shouldParseTitleRelations() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then
      List<SerialTitleRelated> relations = record.titleRelations();
      assertThat(relations).hasSize(2);

      // 前身期刊（带 ISSN）
      SerialTitleRelated preceding = relations.getFirst();
      assertThat(preceding.titleType()).isEqualTo("Preceding");
      assertThat(preceding.relatedTitle()).isEqualTo("Old Journal of Test");
      assertThat(preceding.hasIssn()).isTrue();
      assertThat(preceding.relatedIssn()).isEqualTo("0000-1111");

      // 后继期刊（无 ISSN）
      SerialTitleRelated succeeding = relations.get(1);
      assertThat(succeeding.titleType()).isEqualTo("Succeeding");
      assertThat(succeeding.relatedTitle()).isEqualTo("New Journal of Test");
      assertThat(succeeding.hasIssn()).isFalse();
    }

    @Test
    @DisplayName("应正确解析索引历史列表")
    void shouldParseIndexingHistories() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then
      List<SerialIndexingHistory> histories = record.indexingHistories();
      assertThat(histories).hasSize(2);

      // 第一条：当前正在索引
      SerialIndexingHistory first = histories.getFirst();
      assertThat(first.citationSubset()).isEqualTo("IM");
      assertThat(first.indexingTreatment()).isEqualTo("Full");
      assertThat(first.indexingStatus()).isEqualTo("Currently-indexed");
      assertThat(first.isCurrentlyIndexed()).isTrue();
      assertThat(first.isMedlineIndexing()).isTrue();
      assertThat(first.dateOfAction()).isEqualTo(LocalDate.of(2010, 1, 15));
      assertThat(first.coverage()).isEqualTo("v1n1, 1990-");
      assertThat(first.coverageNote()).isEqualTo("Indexed from volume 1 issue 1");

      // 第二条：已取消选择
      SerialIndexingHistory second = histories.get(1);
      assertThat(second.citationSubset()).isEqualTo("AIM");
      assertThat(second.indexingStatus()).isEqualTo("Deselected");
      assertThat(second.isCurrentlyIndexed()).isFalse();
    }
  }

  @Nested
  @DisplayName("多条记录解析测试")
  class MultipleRecordsTest {

    @Test
    @DisplayName("应正确解析多条记录")
    void shouldParseMultipleRecords() throws Exception {
      // Given
      List<SerialRecord> records = parseAllSerials("/serfile/multiple-serials.xml");

      // Then
      assertThat(records).hasSize(3);

      // 验证第一条
      assertThat(records.get(0).nlmUniqueId()).isEqualTo("0000001");
      assertThat(records.get(0).title()).isEqualTo("First Journal");
      assertThat(records.get(0).issnPrint()).isEqualTo("1111-1111");

      // 验证第二条
      assertThat(records.get(1).nlmUniqueId()).isEqualTo("0000002");
      assertThat(records.get(1).title()).isEqualTo("Second Journal");
      assertThat(records.get(1).issnElectronic()).isEqualTo("2222-2222");
      assertThat(records.get(1).coden()).isEqualTo("SECJOU");

      // 验证第三条
      assertThat(records.get(2).nlmUniqueId()).isEqualTo("0000003");
      assertThat(records.get(2).title()).isEqualTo("Third Journal");
    }
  }

  @Nested
  @DisplayName("便捷方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("停刊期刊应正确识别")
    void shouldIdentifyCeasedJournal() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then
      assertThat(record.isCeased()).isTrue();
    }

    @Test
    @DisplayName("hasIssnL 应正确判断")
    void shouldCheckHasIssnL() throws Exception {
      // Given
      SerialRecord record = parseFirstSerial("/serfile/full-serial.xml");

      // Then
      assertThat(record.hasIssnL()).isTrue();
      assertThat(record.hasAnyIssn()).isTrue();
      assertThat(record.hasCoden()).isTrue();
      assertThat(record.hasMeshHeadings()).isTrue();
      assertThat(record.hasTitleRelations()).isTrue();
      assertThat(record.hasIndexingHistories()).isTrue();
    }
  }

  // ========== 辅助方法 ==========

  /// 解析 XML 文件中的第一条 Serial 记录。
  private SerialRecord parseFirstSerial(String resourcePath) throws Exception {
    try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      XMLStreamReader reader = factory.createXMLStreamReader(is);

      // 定位到第一个 Serial 元素
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT
            && "Serial".equals(reader.getLocalName())) {
          return strategy.parseRecord(reader, context);
        }
      }
      return null;
    }
  }

  /// 解析 XML 文件中的所有 Serial 记录。
  private List<SerialRecord> parseAllSerials(String resourcePath) throws Exception {
    try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      XMLStreamReader reader = factory.createXMLStreamReader(is);

      java.util.ArrayList<SerialRecord> records = new java.util.ArrayList<>();
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT
            && "Serial".equals(reader.getLocalName())) {
          SerialRecord record = strategy.parseRecord(reader, context);
          if (record != null) {
            records.add(record);
          }
        }
      }
      return records;
    }
  }
}
