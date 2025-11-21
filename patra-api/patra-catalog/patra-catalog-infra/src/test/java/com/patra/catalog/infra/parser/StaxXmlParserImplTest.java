package com.patra.catalog.infra.parser;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StAX XML 解析器单元测试。
 *
 * <p>使用测试 XML 文件验证流式解析功能。
 *
 * <p><b>测试策略</b>：
 *
 * <ul>
 *   <li>单元测试：不依赖真实 XML 文件
 *   <li>测试数据：使用内存中的 XML 字符串
 *   <li>测试覆盖：parseDescriptors()、parseTreeNumbers()、parseEntryTerms()、parseConcepts()
 *   <li>边界情况：空文件、格式错误、缺少必填字段
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("StaxXmlParserImpl 单元测试")
class StaxXmlParserImplTest {

  private final StaxXmlParserImpl xmlParser = new StaxXmlParserImpl();

  @Test
  @DisplayName("解析Descriptor - 应该返回完整的聚合根对象")
  void parseDescriptors_validXml_shouldReturnAggregates() {
    // Given: 测试 XML
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <DescriptorRecordSet>
          <DescriptorRecord>
            <DescriptorUI>D000001</DescriptorUI>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
            <TreeNumberList>
              <TreeNumber>D03.438.221</TreeNumber>
              <TreeNumber>D23.767.249</TreeNumber>
            </TreeNumberList>
          </DescriptorRecord>
          <DescriptorRecord>
            <DescriptorUI>D000002</DescriptorUI>
            <DescriptorName>
              <String>Temefos</String>
            </DescriptorName>
            <TreeNumberList>
              <TreeNumber>D02.705.400</TreeNumber>
            </TreeNumberList>
          </DescriptorRecord>
        </DescriptorRecordSet>
        """;

    InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

    // When: 解析 XML
    try (Stream<MeshDescriptorAggregate> stream = xmlParser.parseDescriptors(inputStream)) {
      List<MeshDescriptorAggregate> descriptors = stream.toList();

      // Then: 应该解析出2个Descriptor
      assertThat(descriptors).hasSize(2);

      // 验证第一个Descriptor
      MeshDescriptorAggregate first = descriptors.get(0);
      assertThat(first.getDescriptorUI()).isEqualTo("D000001");
      assertThat(first.getDescriptorName()).isEqualTo("Calcimycin");
      assertThat(first.getTreeNumbers()).hasSize(2);
      assertThat(first.getTreeNumbers().get(0).getTreeNumberValue()).isEqualTo("D03.438.221");

      // 验证第二个Descriptor
      MeshDescriptorAggregate second = descriptors.get(1);
      assertThat(second.getDescriptorUI()).isEqualTo("D000002");
      assertThat(second.getDescriptorName()).isEqualTo("Temefos");
      assertThat(second.getTreeNumbers()).hasSize(1);
    }
  }

  @Test
  @DisplayName("解析TreeNumber - 应该返回所有树形编号")
  void parseTreeNumbers_validXml_shouldReturnTreeNumbers() {
    // Given: 测试 XML
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <DescriptorRecordSet>
          <DescriptorRecord>
            <DescriptorUI>D000001</DescriptorUI>
            <TreeNumberList>
              <TreeNumber>D03.438.221</TreeNumber>
              <TreeNumber>D23.767.249</TreeNumber>
            </TreeNumberList>
          </DescriptorRecord>
        </DescriptorRecordSet>
        """;

    InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

    // When: 解析树形编号
    try (Stream<MeshTreeNumber> stream = xmlParser.parseTreeNumbers(inputStream)) {
      List<MeshTreeNumber> treeNumbers = stream.toList();

      // Then: 应该解析出2个TreeNumber
      assertThat(treeNumbers).hasSize(2);
      assertThat(treeNumbers.get(0).getTreeNumberValue()).isEqualTo("D03.438.221");
      assertThat(treeNumbers.get(1).getTreeNumberValue()).isEqualTo("D23.767.249");
    }
  }

  @Test
  @DisplayName("解析EntryTerm - 应该返回所有入口术语")
  void parseEntryTerms_validXml_shouldReturnEntryTerms() {
    // Given: 测试 XML
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <DescriptorRecordSet>
          <DescriptorRecord>
            <DescriptorUI>D000001</DescriptorUI>
            <ConceptList>
              <Concept>
                <ConceptUI>M0000001</ConceptUI>
                <TermList>
                  <Term>
                    <TermUI>T000001</TermUI>
                    <String>Calcimycin</String>
                  </Term>
                  <Term>
                    <TermUI>T000002</TermUI>
                    <String>A-23187</String>
                  </Term>
                </TermList>
              </Concept>
            </ConceptList>
          </DescriptorRecord>
        </DescriptorRecordSet>
        """;

    InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

    // When: 解析入口术语
    try (Stream<MeshEntryTerm> stream = xmlParser.parseEntryTerms(inputStream)) {
      List<MeshEntryTerm> entryTerms = stream.toList();

      // Then: 应该解析出2个EntryTerm
      assertThat(entryTerms).hasSize(2);
      assertThat(entryTerms.get(0).getTermString()).isEqualTo("Calcimycin");
      assertThat(entryTerms.get(1).getTermString()).isEqualTo("A-23187");
    }
  }

  @Test
  @DisplayName("解析Concept - 应该返回所有概念")
  void parseConcepts_validXml_shouldReturnConcepts() {
    // Given: 测试 XML
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <DescriptorRecordSet>
          <DescriptorRecord>
            <DescriptorUI>D000001</DescriptorUI>
            <ConceptList>
              <Concept>
                <ConceptUI>M0000001</ConceptUI>
                <ConceptName>
                  <String>Calcimycin</String>
                </ConceptName>
              </Concept>
              <Concept>
                <ConceptUI>M0000002</ConceptUI>
                <ConceptName>
                  <String>A23187</String>
                </ConceptName>
              </Concept>
            </ConceptList>
          </DescriptorRecord>
        </DescriptorRecordSet>
        """;

    InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

    // When: 解析概念
    try (Stream<MeshConcept> stream = xmlParser.parseConcepts(inputStream)) {
      List<MeshConcept> concepts = stream.toList();

      // Then: 应该解析出2个Concept
      assertThat(concepts).hasSize(2);
      assertThat(concepts.get(0).getConceptUI()).isEqualTo("M0000001");
      assertThat(concepts.get(1).getConceptUI()).isEqualTo("M0000002");
    }
  }

  @Test
  @DisplayName("解析空文件 - 应该返回空流")
  void parseDescriptors_emptyXml_shouldReturnEmptyStream() {
    // Given: 空 XML
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <DescriptorRecordSet>
        </DescriptorRecordSet>
        """;

    InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

    // When: 解析 XML
    try (Stream<MeshDescriptorAggregate> stream = xmlParser.parseDescriptors(inputStream)) {
      List<MeshDescriptorAggregate> descriptors = stream.toList();

      // Then: 应该返回空列表
      assertThat(descriptors).isEmpty();
    }
  }

  @Test
  @DisplayName("解析格式错误的XML - 应该抛出异常")
  void parseDescriptors_malformedXml_shouldThrowException() {
    // Given: 格式错误的 XML
    String xml = "<DescriptorRecordSet><DescriptorRecord></DescriptorRecordSet>";

    InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

    // When & Then: 应该抛出异常
    assertThatThrownBy(() -> {
          try (Stream<MeshDescriptorAggregate> stream = xmlParser.parseDescriptors(inputStream)) {
            stream.toList(); // 触发解析
          }
        })
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("解析缺少必填字段的XML - 应该跳过该记录")
  void parseDescriptors_missingRequiredField_shouldSkipRecord() {
    // Given: 缺少 DescriptorUI 的 XML
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <DescriptorRecordSet>
          <DescriptorRecord>
            <DescriptorName>
              <String>Calcimycin</String>
            </DescriptorName>
          </DescriptorRecord>
          <DescriptorRecord>
            <DescriptorUI>D000002</DescriptorUI>
            <DescriptorName>
              <String>Temefos</String>
            </DescriptorName>
          </DescriptorRecord>
        </DescriptorRecordSet>
        """;

    InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

    // When: 解析 XML
    try (Stream<MeshDescriptorAggregate> stream = xmlParser.parseDescriptors(inputStream)) {
      List<MeshDescriptorAggregate> descriptors = stream.toList();

      // Then: 应该只解析出有效记录
      assertThat(descriptors).hasSize(1);
      assertThat(descriptors.get(0).getDescriptorUI()).isEqualTo("D000002");
    }
  }
}
