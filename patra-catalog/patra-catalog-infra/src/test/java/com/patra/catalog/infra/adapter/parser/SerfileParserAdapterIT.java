package com.patra.catalog.infra.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.exception.XmlParseException;
import com.patra.catalog.domain.model.dto.serfile.SerialRecord;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// SerfileParserAdapter 集成测试。
///
/// 使用测试 XML 文件验证 NLM Serfile 解析逻辑的正确性。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SerfileParserAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SerfileParserAdapterIT {

  private static final Path TEST_SERIALS_PATH = Path.of("src/test/resources/xml/test-serials.xml");
  private static final Path NON_EXISTENT_PATH = Path.of("src/test/resources/xml/non-existent.xml");

  private SerfileParserAdapter parser;

  @BeforeEach
  void setUp() {
    parser = new SerfileParserAdapter();
  }

  @Nested
  @DisplayName("parse() 正常场景测试")
  class ParseTest {

    @Test
    @DisplayName("解析测试 XML - 应该返回 3 条期刊记录")
    void parse_testXml_shouldReturnThreeSerials() {
      // Given
      Path xmlPath = TEST_SERIALS_PATH;

      // When
      List<SerialRecord> serials;
      try (Stream<SerialRecord> stream = parser.parse(xmlPath)) {
        serials = stream.toList();
      }

      // Then
      assertThat(serials).hasSize(3);
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析基本信息")
    void parse_shouldParseBasicInfoCorrectly() {
      // Given
      Path xmlPath = TEST_SERIALS_PATH;

      // When
      List<SerialRecord> serials;
      try (Stream<SerialRecord> stream = parser.parse(xmlPath)) {
        serials = stream.toList();
      }

      // Then: 验证第一条记录的基本信息
      SerialRecord first = serials.get(0);
      assertThat(first.nlmUniqueId()).isEqualTo("0123456");
      assertThat(first.title()).isEqualTo("Journal of Test Medicine");
      assertThat(first.medlineTA()).isEqualTo("J Test Med");
      assertThat(first.coden()).isEqualTo("JTMED1");
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析 ISSN 信息")
    void parse_shouldParseIssnCorrectly() {
      // Given
      Path xmlPath = TEST_SERIALS_PATH;

      // When
      List<SerialRecord> serials;
      try (Stream<SerialRecord> stream = parser.parse(xmlPath)) {
        serials = stream.toList();
      }

      // Then
      SerialRecord first = serials.get(0);
      assertThat(first.issnL()).isEqualTo("1234-5678");
      assertThat(first.issnPrint()).isEqualTo("1234-5678");
      assertThat(first.issnElectronic()).isEqualTo("1234-5679");
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析语言列表")
    void parse_shouldParseLanguagesCorrectly() {
      // Given
      Path xmlPath = TEST_SERIALS_PATH;

      // When
      List<SerialRecord> serials;
      try (Stream<SerialRecord> stream = parser.parse(xmlPath)) {
        serials = stream.toList();
      }

      // Then
      SerialRecord first = serials.get(0);
      assertThat(first.languages()).containsExactly("eng", "chi");
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析出版信息")
    void parse_shouldParsePublicationInfoCorrectly() {
      // Given
      Path xmlPath = TEST_SERIALS_PATH;

      // When
      List<SerialRecord> serials;
      try (Stream<SerialRecord> stream = parser.parse(xmlPath)) {
        serials = stream.toList();
      }

      // Then: 第一条记录
      SerialRecord first = serials.get(0);
      assertThat(first.country()).isEqualTo("United States");
      assertThat(first.frequency()).isEqualTo("Monthly");
      assertThat(first.publicationFirstYear()).isEqualTo(2000);
      assertThat(first.publicationEndYear()).isNull();

      // 第二条记录（已停刊）
      SerialRecord second = serials.get(1);
      assertThat(second.country()).isEqualTo("China");
      assertThat(second.publicationFirstYear()).isEqualTo(1990);
      assertThat(second.publicationEndYear()).isEqualTo(2020);
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析 MeSH 主题词")
    void parse_shouldParseMeshHeadingsCorrectly() {
      // Given
      Path xmlPath = TEST_SERIALS_PATH;

      // When
      List<SerialRecord> serials;
      try (Stream<SerialRecord> stream = parser.parse(xmlPath)) {
        serials = stream.toList();
      }

      // Then
      SerialRecord first = serials.get(0);
      assertThat(first.meshHeadings()).hasSize(2);
      assertThat(first.meshHeadings().get(0).descriptorName()).isEqualTo("Medicine");
      assertThat(first.meshHeadings().get(0).isMajorTopic()).isTrue();
      assertThat(first.meshHeadings().get(1).qualifierName()).isEqualTo("methods");
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析期刊关联")
    void parse_shouldParseTitleRelationsCorrectly() {
      // Given
      Path xmlPath = TEST_SERIALS_PATH;

      // When
      List<SerialRecord> serials;
      try (Stream<SerialRecord> stream = parser.parse(xmlPath)) {
        serials = stream.toList();
      }

      // Then: 第三条记录有 2 个关联
      SerialRecord third = serials.get(2);
      assertThat(third.titleRelations()).hasSize(2);
      assertThat(third.titleRelations().get(0).titleType()).isEqualTo("Continues");
      assertThat(third.titleRelations().get(0).relatedTitle()).isEqualTo("Old Test Journal");
      assertThat(third.titleRelations().get(1).titleType()).isEqualTo("ContinuedBy");
    }
  }

  @Nested
  @DisplayName("parse() 异常场景测试")
  class ExceptionTest {

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

  @Nested
  @DisplayName("Stream 资源释放测试")
  class StreamResourceReleaseTest {

    @Test
    @DisplayName("Stream 关闭后 - 不应该抛出异常")
    void streamClose_shouldNotThrowException() {
      // Given
      Path xmlPath = TEST_SERIALS_PATH;

      // When: 打开并关闭 Stream
      Stream<SerialRecord> stream = parser.parse(xmlPath);
      stream.close();

      // Then: 不应该抛出异常
    }

    @Test
    @DisplayName("try-with-resources - 应该正确释放资源")
    void tryWithResources_shouldReleaseResourcesCorrectly() {
      // Given & When & Then: 使用 try-with-resources 自动关闭
      try (Stream<SerialRecord> stream = parser.parse(TEST_SERIALS_PATH)) {
        // 只读取第一个元素
        SerialRecord first = stream.findFirst().orElse(null);
        assertThat(first).isNotNull();
        assertThat(first.nlmUniqueId()).isEqualTo("0123456");
      }
      // 资源应该在这里被正确释放
    }
  }
}
