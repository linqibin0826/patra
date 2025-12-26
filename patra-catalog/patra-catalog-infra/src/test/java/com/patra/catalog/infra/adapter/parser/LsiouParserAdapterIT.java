package com.patra.catalog.infra.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.exception.XmlParseException;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedLanguage;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// LsiouParserAdapter 集成测试。
///
/// 使用测试 XML 文件验证 NLM LSIOU 解析逻辑的正确性。
///
/// **流式处理**：
///
/// 解析器现在接收 `InputStream` 而非 `Path`，测试使用 `Files.newInputStream()` 读取文件。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LsiouParserAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class LsiouParserAdapterIT {

  private static final Path TEST_SERIALS_PATH = Path.of("src/test/resources/xml/test-serials.xml");

  private LsiouParserAdapter parser;

  @BeforeEach
  void setUp() {
    parser = new LsiouParserAdapter();
  }

  /// 打开测试 XML 文件的输入流。
  private InputStream openTestXmlStream() throws IOException {
    return Files.newInputStream(TEST_SERIALS_PATH);
  }

  @Nested
  @DisplayName("parse() 正常场景测试")
  class ParseTest {

    @Test
    @DisplayName("解析测试 XML - 应该返回 3 条期刊记录")
    void parse_testXml_shouldReturnThreeSerials() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<PubmedSerialData> serials;
        try (Stream<PubmedSerialData> stream = parser.parse(inputStream)) {
          serials = stream.toList();
        }

        // Then
        assertThat(serials).hasSize(3);
      }
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析基本信息")
    void parse_shouldParseBasicInfoCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<PubmedSerialData> serials;
        try (Stream<PubmedSerialData> stream = parser.parse(inputStream)) {
          serials = stream.toList();
        }

        // Then: 验证第一条记录的基本信息
        PubmedSerialData first = serials.get(0);
        assertThat(first.nlmUniqueId()).isEqualTo("0123456");
        assertThat(first.title()).isEqualTo("Journal of Test Medicine");
        assertThat(first.medlineTA()).isEqualTo("J Test Med");
        assertThat(first.coden()).isEqualTo("JTMED1");
      }
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析 ISSN 信息")
    void parse_shouldParseIssnCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<PubmedSerialData> serials;
        try (Stream<PubmedSerialData> stream = parser.parse(inputStream)) {
          serials = stream.toList();
        }

        // Then
        PubmedSerialData first = serials.get(0);
        assertThat(first.issnL()).isEqualTo("1234-5678");
        assertThat(first.issnPrint()).isEqualTo("1234-5678");
        assertThat(first.issnElectronic()).isEqualTo("1234-5679");
      }
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析语言列表")
    void parse_shouldParseLanguagesCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<PubmedSerialData> serials;
        try (Stream<PubmedSerialData> stream = parser.parse(inputStream)) {
          serials = stream.toList();
        }

        // Then
        PubmedSerialData first = serials.get(0);
        assertThat(first.languages())
            .extracting(PubmedLanguage::code)
            .containsExactly("eng", "chi");
      }
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析出版信息")
    void parse_shouldParsePublicationInfoCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<PubmedSerialData> serials;
        try (Stream<PubmedSerialData> stream = parser.parse(inputStream)) {
          serials = stream.toList();
        }

        // Then: 第一条记录
        PubmedSerialData first = serials.get(0);
        assertThat(first.country()).isEqualTo("United States");
        assertThat(first.frequency()).isEqualTo("Monthly");
        assertThat(first.publicationFirstYear()).isEqualTo(2000);
        assertThat(first.publicationEndYear()).isNull();

        // 第二条记录（已停刊）
        PubmedSerialData second = serials.get(1);
        assertThat(second.country()).isEqualTo("China");
        assertThat(second.publicationFirstYear()).isEqualTo(1990);
        assertThat(second.publicationEndYear()).isEqualTo(2020);
      }
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析 MeSH 主题词")
    void parse_shouldParseMeshHeadingsCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<PubmedSerialData> serials;
        try (Stream<PubmedSerialData> stream = parser.parse(inputStream)) {
          serials = stream.toList();
        }

        // Then
        PubmedSerialData first = serials.get(0);
        assertThat(first.meshHeadings()).hasSize(2);
        assertThat(first.meshHeadings().get(0).descriptorName()).isEqualTo("Medicine");
        assertThat(first.meshHeadings().get(0).isMajorTopic()).isTrue();
        assertThat(first.meshHeadings().get(1).qualifierName()).isEqualTo("methods");
      }
    }

    @Test
    @DisplayName("解析期刊 - 应该正确解析期刊关联")
    void parse_shouldParseTitleRelationsCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<PubmedSerialData> serials;
        try (Stream<PubmedSerialData> stream = parser.parse(inputStream)) {
          serials = stream.toList();
        }

        // Then: 第三条记录有 2 个关联
        PubmedSerialData third = serials.get(2);
        assertThat(third.titleRelations()).hasSize(2);
        assertThat(third.titleRelations().get(0).relatedTitle()).isEqualTo("Old Test Journal");
        assertThat(third.titleRelations().get(1).relatedTitle()).isNotNull();
      }
    }
  }

  @Nested
  @DisplayName("parse() 异常场景测试")
  class ExceptionTest {

    @Test
    @DisplayName("无效 XML 格式 - 应该抛出 XmlParseException")
    void parse_invalidXml_shouldThrowXmlParseException() {
      // Given: 无效的 XML 内容
      String invalidXml = "Not valid XML content <<>>";
      InputStream invalidStream =
          new ByteArrayInputStream(invalidXml.getBytes(StandardCharsets.UTF_8));

      // When & Then: 异常在消费 Stream 时抛出（StAX 惰性解析）
      assertThatThrownBy(
              () -> {
                try (Stream<PubmedSerialData> stream = parser.parse(invalidStream)) {
                  stream.findFirst(); // 触发实际解析
                }
              })
          .isInstanceOf(XmlParseException.class)
          .hasMessageContaining("XML 解析失败");
    }
  }

  @Nested
  @DisplayName("Stream 资源释放测试")
  class StreamResourceReleaseTest {

    @Test
    @DisplayName("Stream 关闭后 - 不应该抛出异常")
    void streamClose_shouldNotThrowException() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When: 打开并关闭 Stream
        Stream<PubmedSerialData> stream = parser.parse(inputStream);
        stream.close();

        // Then: 不应该抛出异常
      }
    }

    @Test
    @DisplayName("try-with-resources - 应该正确释放资源")
    void tryWithResources_shouldReleaseResourcesCorrectly() throws IOException {
      // Given & When & Then: 使用 try-with-resources 自动关闭
      try (InputStream inputStream = openTestXmlStream();
          Stream<PubmedSerialData> stream = parser.parse(inputStream)) {
        // 只读取第一个元素
        PubmedSerialData first = stream.findFirst().orElse(null);
        assertThat(first).isNotNull();
        assertThat(first.nlmUniqueId()).isEqualTo("0123456");
      }
      // 资源应该在这里被正确释放
    }
  }
}
