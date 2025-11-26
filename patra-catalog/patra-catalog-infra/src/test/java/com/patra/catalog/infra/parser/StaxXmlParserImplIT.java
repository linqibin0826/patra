package com.patra.catalog.infra.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.infra.adapter.parser.XmlParserAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// StaxXmlParserImpl 集成测试。
///
/// 使用测试 XML 文件验证 XML 解析逻辑的正确性。
///
/// **测试策略**：
///
/// - 集成测试：使用真实的 XML 文件测试解析逻辑
///   - 测试隔离：每个测试方法独立，无共享状态
///   - 测试覆盖：parseQualifiers()、parseDescriptors()、parseTreeNumbers()、
///     parseEntryTerms()、parseConcepts() 的各种场景
///
/// **重点测试场景**：
///
/// - parseQualifiers()：解析限定词 XML 文件
///   - parseDescriptors()：解析主题词 XML 文件
///   - parseTreeNumbers()：解析树形编号
///   - parseEntryTerms()：解析入口术语
///   - parseConcepts()：解析概念
///   - Stream 资源释放：验证 Stream 关闭后资源正确释放
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("StaxXmlParserImpl 集成测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class StaxXmlParserImplIT {

  private static final String TEST_MESH_VERSION = "2025";
  private static final Path TEST_QUALIFIERS_PATH =
      Path.of("src/test/resources/xml/test-qualifiers.xml");
  private static final String TEST_DESCRIPTORS_RESOURCE = "/xml/test-descriptors.xml";

  private XmlParserAdapter xmlParser;

  @BeforeEach
  void setUp() {
    xmlParser = new XmlParserAdapter();
  }

  @Nested
  @DisplayName("parseQualifiers() 测试")
  class ParseQualifiersTest {

    @Test
    @DisplayName("解析测试 XML - 应该返回 3 个限定词聚合根（包含 10 位 UI 格式）")
    void parseQualifiers_testXml_shouldReturnThreeQualifiers() {
      // Given: 测试 XML 文件路径
      Path xmlPath = TEST_QUALIFIERS_PATH;

      // When: 解析限定词
      List<MeshQualifierAggregate> qualifiers;
      try (Stream<MeshQualifierAggregate> stream =
          xmlParser.parseQualifiers(xmlPath, TEST_MESH_VERSION)) {
        qualifiers = stream.toList();
      }

      // Then: 验证返回 3 个限定词（2 个 7 位 + 1 个 10 位）
      assertThat(qualifiers).hasSize(3);

      // 验证第一个限定词（7 位 UI 格式）
      MeshQualifierAggregate qualifier1 = qualifiers.get(0);
      assertThat(qualifier1.getQualifierUi().ui()).isEqualTo("Q000001");
      assertThat(qualifier1.getName()).isEqualTo("test qualifier 1");
      assertThat(qualifier1.getMeshVersion()).isEqualTo(TEST_MESH_VERSION);
      assertThat(qualifier1.getAbbreviation()).isEqualTo("TQ1");

      // 验证第二个限定词（7 位 UI 格式）
      MeshQualifierAggregate qualifier2 = qualifiers.get(1);
      assertThat(qualifier2.getQualifierUi().ui()).isEqualTo("Q000002");
      assertThat(qualifier2.getName()).isEqualTo("test qualifier 2");
      assertThat(qualifier2.getAbbreviation()).isEqualTo("TQ2");

      // 验证第三个限定词（10 位 UI 格式 - 关键测试：验证 BUG 修复）
      MeshQualifierAggregate qualifier3 = qualifiers.get(2);
      // 修复前：Q000000981 会被错误转换为 Q000981
      // 修复后：Q000000981 应保持原样
      assertThat(qualifier3.getQualifierUi().ui()).isEqualTo("Q000000981");
      assertThat(qualifier3.getName()).isEqualTo("test qualifier 10-digit");
      assertThat(qualifier3.getAbbreviation()).isEqualTo("TQ10");
    }

    @Test
    @DisplayName("解析限定词 - 应该正确解析日期字段")
    void parseQualifiers_shouldParseDateFieldsCorrectly() {
      // Given
      Path xmlPath = TEST_QUALIFIERS_PATH;

      // When
      List<MeshQualifierAggregate> qualifiers;
      try (Stream<MeshQualifierAggregate> stream =
          xmlParser.parseQualifiers(xmlPath, TEST_MESH_VERSION)) {
        qualifiers = stream.toList();
      }

      // Then: 验证日期字段（格式：YYYYMMDD）
      MeshQualifierAggregate qualifier1 = qualifiers.get(0);
      assertThat(qualifier1.getDateCreated()).isNotNull();
      assertThat(qualifier1.getDateCreated()).isEqualTo("20240101");
    }
  }

  @Nested
  @DisplayName("parseDescriptors() 测试")
  class ParseDescriptorsTest {

    @Test
    @DisplayName("解析测试 XML - 应该返回 2 个主题词聚合根")
    void parseDescriptors_testXml_shouldReturnTwoDescriptors() throws IOException {
      // Given: 测试 XML 输入流
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When: 解析主题词
        List<MeshDescriptorAggregate> descriptors;
        try (Stream<MeshDescriptorAggregate> stream =
            xmlParser.parseDescriptors(inputStream, TEST_MESH_VERSION)) {
          descriptors = stream.toList();
        }

        // Then: 验证返回 2 个主题词
        assertThat(descriptors).hasSize(2);

        // 验证第一个主题词
        MeshDescriptorAggregate descriptor1 = descriptors.get(0);
        assertThat(descriptor1.getUi().ui()).isEqualTo("D000001");
        assertThat(descriptor1.getName()).isEqualTo("Test Descriptor 1");
        assertThat(descriptor1.getMeshVersion()).isEqualTo(TEST_MESH_VERSION);
        assertThat(descriptor1.getDescriptorClass().getCode()).isEqualTo("1");

        // 验证第二个主题词
        MeshDescriptorAggregate descriptor2 = descriptors.get(1);
        assertThat(descriptor2.getUi().ui()).isEqualTo("D000002");
        assertThat(descriptor2.getName()).isEqualTo("Test Descriptor 2");
        assertThat(descriptor2.getDescriptorClass().getCode()).isEqualTo("2");
      }
    }

    @Test
    @DisplayName("解析主题词 - 应该正确解析树形编号列表")
    void parseDescriptors_shouldParseTreeNumbersCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When
        List<MeshDescriptorAggregate> descriptors;
        try (Stream<MeshDescriptorAggregate> stream =
            xmlParser.parseDescriptors(inputStream, TEST_MESH_VERSION)) {
          descriptors = stream.toList();
        }

        // Then: 第一个主题词有 2 个树形编号
        MeshDescriptorAggregate descriptor1 = descriptors.get(0);
        assertThat(descriptor1.getTreeNumbers()).hasSize(2);
        assertThat(descriptor1.getTreeNumbers())
            .extracting(MeshTreeNumber::getTreeNumber)
            .containsExactlyInAnyOrder("A01.001", "B02.002");

        // 第二个主题词有 1 个树形编号
        MeshDescriptorAggregate descriptor2 = descriptors.get(1);
        assertThat(descriptor2.getTreeNumbers()).hasSize(1);
        assertThat(descriptor2.getTreeNumbers().get(0).getTreeNumber()).isEqualTo("C03.003");
      }
    }

    @Test
    @DisplayName("解析主题词 - 应该正确解析允许限定词列表")
    void parseDescriptors_shouldParseAllowableQualifiersCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When
        List<MeshDescriptorAggregate> descriptors;
        try (Stream<MeshDescriptorAggregate> stream =
            xmlParser.parseDescriptors(inputStream, TEST_MESH_VERSION)) {
          descriptors = stream.toList();
        }

        // Then: 第一个主题词有允许限定词
        MeshDescriptorAggregate descriptor1 = descriptors.get(0);
        assertThat(descriptor1.getAllowableQualifiers()).isNotEmpty();
        assertThat(descriptor1.getAllowableQualifiers().get(0).qualifierUi().ui())
            .isEqualTo("Q000001");
        assertThat(descriptor1.getAllowableQualifiers().get(0).abbreviation()).isEqualTo("TQ");
      }
    }
  }

  @Nested
  @DisplayName("parseTreeNumbers() 测试")
  class ParseTreeNumbersTest {

    @Test
    @DisplayName("解析树形编号 - 应该返回所有树形编号")
    void parseTreeNumbers_shouldReturnAllTreeNumbers() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When
        List<MeshTreeNumber> treeNumbers;
        try (Stream<MeshTreeNumber> stream = xmlParser.parseTreeNumbers(inputStream)) {
          treeNumbers = stream.toList();
        }

        // Then: 应该有 3 个树形编号（第一个主题词 2 个 + 第二个主题词 1 个）
        assertThat(treeNumbers).hasSize(3);
        assertThat(treeNumbers)
            .extracting(MeshTreeNumber::getTreeNumber)
            .containsExactlyInAnyOrder("A01.001", "B02.002", "C03.003");
      }
    }
  }

  @Nested
  @DisplayName("parseEntryTerms() 测试")
  class ParseEntryTermsTest {

    @Test
    @DisplayName("解析入口术语 - 应该返回所有入口术语")
    void parseEntryTerms_shouldReturnAllEntryTerms() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When
        List<MeshEntryTerm> entryTerms;
        try (Stream<MeshEntryTerm> stream = xmlParser.parseEntryTerms(inputStream)) {
          entryTerms = stream.toList();
        }

        // Then: 应该有 4 个入口术语（第一个主题词 3 个 + 第二个主题词 1 个）
        assertThat(entryTerms).hasSize(4);
        assertThat(entryTerms)
            .extracting(et -> et.getTermUi().ui())
            .containsExactlyInAnyOrder("T000001", "T000002", "T000003", "T000004");
      }
    }

    @Test
    @DisplayName("解析入口术语 - 应该正确解析术语属性")
    void parseEntryTerms_shouldParseTermAttributesCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When
        List<MeshEntryTerm> entryTerms;
        try (Stream<MeshEntryTerm> stream = xmlParser.parseEntryTerms(inputStream)) {
          entryTerms = stream.toList();
        }

        // Then: 验证第一个术语的属性
        MeshEntryTerm term1 =
            entryTerms.stream()
                .filter(t -> t.getTermUi().ui().equals("T000001"))
                .findFirst()
                .orElseThrow();

        assertThat(term1.getTerm()).isEqualTo("Test Term 1");
        assertThat(term1.isConceptPreferred()).isTrue();
        assertThat(term1.isRecordPreferred()).isTrue();
      }
    }
  }

  @Nested
  @DisplayName("parseConcepts() 测试")
  class ParseConceptsTest {

    @Test
    @DisplayName("解析概念 - 应该返回所有概念")
    void parseConcepts_shouldReturnAllConcepts() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When
        List<MeshConcept> concepts;
        try (Stream<MeshConcept> stream = xmlParser.parseConcepts(inputStream)) {
          concepts = stream.toList();
        }

        // Then: 应该有 3 个概念（第一个主题词 2 个 + 第二个主题词 1 个）
        assertThat(concepts).hasSize(3);
        assertThat(concepts)
            .extracting(c -> c.getConceptUi().ui())
            .containsExactlyInAnyOrder("M0000001", "M0000002", "M0000003");
      }
    }

    @Test
    @DisplayName("解析概念 - 应该正确解析概念属性")
    void parseConcepts_shouldParseConceptAttributesCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When
        List<MeshConcept> concepts;
        try (Stream<MeshConcept> stream = xmlParser.parseConcepts(inputStream)) {
          concepts = stream.toList();
        }

        // Then: 验证首选概念属性
        MeshConcept concept1 =
            concepts.stream()
                .filter(c -> c.getConceptUi().ui().equals("M0000001"))
                .findFirst()
                .orElseThrow();

        assertThat(concept1.getConceptName()).isEqualTo("Test Concept 1");
        assertThat(concept1.isPreferred()).isTrue();
        assertThat(concept1.getScopeNote()).contains("Test scope note for concept 1");
      }
    }

    @Test
    @DisplayName("解析概念 - 应该正确解析相关注册号列表")
    void parseConcepts_shouldParseRelatedRegistryNumbersCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When
        List<MeshConcept> concepts;
        try (Stream<MeshConcept> stream = xmlParser.parseConcepts(inputStream)) {
          concepts = stream.toList();
        }

        // Then: 验证相关注册号
        MeshConcept concept1 =
            concepts.stream()
                .filter(c -> c.getConceptUi().ui().equals("M0000001"))
                .findFirst()
                .orElseThrow();

        assertThat(concept1.getRelatedRegistryNumbers())
            .containsExactlyInAnyOrder("11111-11-1", "22222-22-2");
      }
    }
  }

  @Nested
  @DisplayName("Stream 资源释放测试")
  class StreamResourceReleaseTest {

    @Test
    @DisplayName("Stream 关闭后 - 不应该抛出异常")
    void streamClose_shouldNotThrowException() throws IOException {
      // Given
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE)) {
        assertThat(inputStream).isNotNull();

        // When: 打开并关闭 Stream
        Stream<MeshDescriptorAggregate> stream =
            xmlParser.parseDescriptors(inputStream, TEST_MESH_VERSION);
        stream.close();

        // Then: 不应该抛出异常
        // 如果执行到这里说明测试通过
      }
    }

    @Test
    @DisplayName("try-with-resources - 应该正确释放资源")
    void tryWithResources_shouldReleaseResourcesCorrectly() throws IOException {
      // Given & When & Then: 使用 try-with-resources 自动关闭
      try (InputStream inputStream = getClass().getResourceAsStream(TEST_DESCRIPTORS_RESOURCE);
          Stream<MeshDescriptorAggregate> stream =
              xmlParser.parseDescriptors(inputStream, TEST_MESH_VERSION)) {
        assertThat(inputStream).isNotNull();

        // 只读取第一个元素
        MeshDescriptorAggregate first = stream.findFirst().orElse(null);
        assertThat(first).isNotNull();
        assertThat(first.getUi().ui()).isEqualTo("D000001");
      }
      // 资源应该在这里被正确释放
    }
  }
}
