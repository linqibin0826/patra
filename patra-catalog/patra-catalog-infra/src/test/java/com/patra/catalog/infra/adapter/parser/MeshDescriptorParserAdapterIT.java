package com.patra.catalog.infra.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.exception.XmlParseException;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// MeshDescriptorParserAdapter 集成测试。
///
/// 使用测试 XML 文件验证主题词解析逻辑的正确性。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshDescriptorParserAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshDescriptorParserAdapterIT {

  private static final String TEST_MESH_VERSION = "2025";
  private static final Path TEST_DESCRIPTORS_PATH =
      Path.of("src/test/resources/xml/test-descriptors.xml");

  private MeshDescriptorParserAdapter parser;

  @BeforeEach
  void setUp() {
    parser = new MeshDescriptorParserAdapter();
  }

  @Nested
  @DisplayName("parse() 测试")
  class ParseTest {

    @Test
    @DisplayName("解析测试 XML - 应该返回 2 个主题词聚合根")
    void parse_testXml_shouldReturnTwoDescriptors() {
      // Given: 测试 XML 文件路径
      Path xmlPath = TEST_DESCRIPTORS_PATH;

      // When: 解析主题词
      List<MeshDescriptorAggregate> descriptors;
      try (Stream<MeshDescriptorAggregate> stream = parser.parse(xmlPath)) {
        descriptors = stream.toList();
      }

      // Then: 验证返回 2 个主题词
      assertThat(descriptors).hasSize(2);

      // 验证第一个主题词
      MeshDescriptorAggregate descriptor1 = descriptors.get(0);
      assertThat(descriptor1.getUi().ui()).isEqualTo("D000001");
      assertThat(descriptor1.getName()).isEqualTo("Test Descriptor 1");
      // meshVersion 由调用方设置，Parser 不再设置
      assertThat(descriptor1.getMeshVersion()).isNull();
      assertThat(descriptor1.getDescriptorClass().getCode()).isEqualTo("1");

      // 验证第二个主题词
      MeshDescriptorAggregate descriptor2 = descriptors.get(1);
      assertThat(descriptor2.getUi().ui()).isEqualTo("D000002");
      assertThat(descriptor2.getName()).isEqualTo("Test Descriptor 2");
      assertThat(descriptor2.getDescriptorClass().getCode()).isEqualTo("2");
    }

    @Test
    @DisplayName("解析主题词 - 应该正确解析树形编号列表")
    void parse_shouldParseTreeNumbersCorrectly() {
      // Given
      Path xmlPath = TEST_DESCRIPTORS_PATH;

      // When
      List<MeshDescriptorAggregate> descriptors;
      try (Stream<MeshDescriptorAggregate> stream = parser.parse(xmlPath)) {
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

    @Test
    @DisplayName("解析主题词 - 应该正确解析允许限定词列表")
    void parse_shouldParseAllowableQualifiersCorrectly() {
      // Given
      Path xmlPath = TEST_DESCRIPTORS_PATH;

      // When
      List<MeshDescriptorAggregate> descriptors;
      try (Stream<MeshDescriptorAggregate> stream = parser.parse(xmlPath)) {
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

  @Nested
  @DisplayName("PublicMeSHNote 解析测试")
  class PublicMeshNoteTest {

    @Test
    @DisplayName("解析主题词 - 应该正确解析 PublicMeSHNote 字段")
    void parse_shouldParsePublicMeshNoteCorrectly() {
      // Given
      Path xmlPath = TEST_DESCRIPTORS_PATH;

      // When
      List<MeshDescriptorAggregate> descriptors;
      try (Stream<MeshDescriptorAggregate> stream = parser.parse(xmlPath)) {
        descriptors = stream.toList();
      }

      // Then: 第一个主题词有 PublicMeSHNote
      MeshDescriptorAggregate descriptor1 = descriptors.get(0);
      assertThat(descriptor1.getPublicMeshNote()).isEqualTo("Test public note");

      // 第二个主题词没有 PublicMeSHNote，应该为 null
      MeshDescriptorAggregate descriptor2 = descriptors.get(1);
      assertThat(descriptor2.getPublicMeshNote()).isNull();
    }
  }

  @Nested
  @DisplayName("EntryCombination 解析测试")
  class EntryCombinationTest {

    @Test
    @DisplayName("解析主题词 - 应该正确解析 EntryCombinationList")
    void parse_shouldParseEntryCombinationsCorrectly() {
      // Given
      Path xmlPath = TEST_DESCRIPTORS_PATH;

      // When
      List<MeshDescriptorAggregate> descriptors;
      try (Stream<MeshDescriptorAggregate> stream = parser.parse(xmlPath)) {
        descriptors = stream.toList();
      }

      // Then: 第一个主题词有 2 个组合条目
      MeshDescriptorAggregate descriptor1 = descriptors.get(0);
      assertThat(descriptor1.getEntryCombinations()).hasSize(2);

      // 验证第一个 EntryCombination（有 ECOUT Qualifier）
      EntryCombination combination1 = descriptor1.getEntryCombinations().get(0);
      assertThat(combination1.ecinDescriptorUi().ui()).isEqualTo("D000001");
      assertThat(combination1.ecinQualifierUi().ui()).isEqualTo("Q000188");
      assertThat(combination1.ecoutDescriptorUi().ui()).isEqualTo("D000002");
      assertThat(combination1.ecoutQualifierUi().ui()).isEqualTo("Q000628");
      assertThat(combination1.hasEcoutQualifier()).isTrue();

      // 验证第二个 EntryCombination（无 ECOUT Qualifier）
      EntryCombination combination2 = descriptor1.getEntryCombinations().get(1);
      assertThat(combination2.ecinDescriptorUi().ui()).isEqualTo("D000001");
      assertThat(combination2.ecinQualifierUi().ui()).isEqualTo("Q000175");
      assertThat(combination2.ecoutDescriptorUi().ui()).isEqualTo("D000003");
      assertThat(combination2.ecoutQualifierUi()).isNull();
      assertThat(combination2.hasEcoutQualifier()).isFalse();

      // 第二个主题词没有组合条目
      MeshDescriptorAggregate descriptor2 = descriptors.get(1);
      assertThat(descriptor2.getEntryCombinations()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Stream 资源释放测试")
  class StreamResourceReleaseTest {

    @Test
    @DisplayName("Stream 关闭后 - 不应该抛出异常")
    void streamClose_shouldNotThrowException() {
      // Given
      Path xmlPath = TEST_DESCRIPTORS_PATH;

      // When: 打开并关闭 Stream
      Stream<MeshDescriptorAggregate> stream = parser.parse(xmlPath);
      stream.close();

      // Then: 不应该抛出异常
    }

    @Test
    @DisplayName("try-with-resources - 应该正确释放资源")
    void tryWithResources_shouldReleaseResourcesCorrectly() {
      // Given & When & Then: 使用 try-with-resources 自动关闭
      try (Stream<MeshDescriptorAggregate> stream = parser.parse(TEST_DESCRIPTORS_PATH)) {
        // 只读取第一个元素
        MeshDescriptorAggregate first = stream.findFirst().orElse(null);
        assertThat(first).isNotNull();
        assertThat(first.getUi().ui()).isEqualTo("D000001");
      }
      // 资源应该在这里被正确释放
    }
  }

  @Nested
  @DisplayName("异常场景测试")
  class ExceptionTest {

    private static final Path NON_EXISTENT_PATH =
        Path.of("src/test/resources/xml/non-existent-descriptor.xml");

    @Test
    @DisplayName("文件不存在时 - 应该抛出 XmlParseException")
    void parse_nonExistentFile_shouldThrowXmlParseException() {
      // Given
      Path nonExistentPath = NON_EXISTENT_PATH;

      // When & Then
      assertThatThrownBy(() -> parser.parse(nonExistentPath))
          .isInstanceOf(XmlParseException.class)
          .hasMessageContaining("打开 XML 文件失败");
    }
  }
}
