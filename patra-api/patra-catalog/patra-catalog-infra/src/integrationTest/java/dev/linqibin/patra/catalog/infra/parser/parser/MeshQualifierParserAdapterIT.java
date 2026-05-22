package dev.linqibin.patra.catalog.infra.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.domain.exception.XmlParseException;
import dev.linqibin.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshQualifierParserAdapter 集成测试。
///
/// 使用测试 XML 文件验证限定词解析逻辑的正确性。
///
/// **流式处理**：
///
/// 解析器现在接收 `InputStream` 而非 `Path`，测试使用 `Files.newInputStream()` 读取文件。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshQualifierParserAdapter 集成测试")
class MeshQualifierParserAdapterIT {

  private static final String TEST_MESH_VERSION = "2025";
  private static final Path TEST_QUALIFIERS_PATH =
      Path.of("src/test/resources/xml/test-qualifiers.xml");

  private MeshQualifierParserAdapter parser;

  @BeforeEach
  void setUp() {
    parser = new MeshQualifierParserAdapter();
  }

  /// 打开测试 XML 文件的输入流。
  private InputStream openTestXmlStream() throws IOException {
    return Files.newInputStream(TEST_QUALIFIERS_PATH);
  }

  @Nested
  @DisplayName("parse() 测试")
  class ParseTest {

    @Test
    @DisplayName("解析测试 XML - 应该返回 3 个限定词聚合根（包含 10 位 UI 格式）")
    void parse_testXml_shouldReturnThreeQualifiers() throws IOException {
      // Given: 测试 XML 文件输入流
      try (InputStream inputStream = openTestXmlStream()) {
        // When: 解析限定词
        List<MeshQualifierAggregate> qualifiers;
        try (Stream<MeshQualifierAggregate> stream = parser.parse(inputStream)) {
          qualifiers = stream.toList();
        }

        // Then: 验证返回 3 个限定词（2 个 7 位 + 1 个 10 位）
        assertThat(qualifiers).hasSize(3);

        // 验证第一个限定词（7 位 UI 格式）
        MeshQualifierAggregate qualifier1 = qualifiers.get(0);
        assertThat(qualifier1.getQualifierUi().ui()).isEqualTo("Q000001");
        assertThat(qualifier1.getName()).isEqualTo("test qualifier 1");
        // meshVersion 由调用方设置，Parser 不再设置
        assertThat(qualifier1.getMeshVersion()).isNull();
        assertThat(qualifier1.getAbbreviation()).isEqualTo("TQ1");

        // 验证第二个限定词（7 位 UI 格式）
        MeshQualifierAggregate qualifier2 = qualifiers.get(1);
        assertThat(qualifier2.getQualifierUi().ui()).isEqualTo("Q000002");
        assertThat(qualifier2.getName()).isEqualTo("test qualifier 2");
        assertThat(qualifier2.getAbbreviation()).isEqualTo("TQ2");

        // 验证第三个限定词（10 位 UI 格式 - 关键测试：验证 BUG 修复）
        MeshQualifierAggregate qualifier3 = qualifiers.get(2);
        assertThat(qualifier3.getQualifierUi().ui()).isEqualTo("Q000000981");
        assertThat(qualifier3.getName()).isEqualTo("test qualifier 10-digit");
        assertThat(qualifier3.getAbbreviation()).isEqualTo("TQ10");
      }
    }

    @Test
    @DisplayName("解析限定词 - 应该正确解析日期字段")
    void parse_shouldParseDateFieldsCorrectly() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<MeshQualifierAggregate> qualifiers;
        try (Stream<MeshQualifierAggregate> stream = parser.parse(inputStream)) {
          qualifiers = stream.toList();
        }

        // Then: 验证日期字段
        MeshQualifierAggregate qualifier1 = qualifiers.get(0);
        assertThat(qualifier1.getDateCreated()).isNotNull();
        assertThat(qualifier1.getDateCreated()).isEqualTo(LocalDate.of(2024, 1, 1));
      }
    }

    @Test
    @DisplayName("解析限定词 - 应该正确解析 HistoryNote 和 OnlineNote")
    void parse_shouldParseHistoryNoteAndOnlineNote() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<MeshQualifierAggregate> qualifiers;
        try (Stream<MeshQualifierAggregate> stream = parser.parse(inputStream)) {
          qualifiers = stream.toList();
        }

        // Then: 验证第一个限定词的 HistoryNote 和 OnlineNote
        MeshQualifierAggregate qualifier1 = qualifiers.get(0);
        assertThat(qualifier1.getHistoryNote())
            .isEqualTo("66; used with Category A 1966-74; test history note");
        assertThat(qualifier1.getOnlineNote())
            .isEqualTo("search policy: Online Manual; use: main heading/TQ1 or TQ1 (SH)");

        // 验证第二个限定词
        MeshQualifierAggregate qualifier2 = qualifiers.get(1);
        assertThat(qualifier2.getHistoryNote())
            .isEqualTo("75; used with Category D 1975 forward; test history note 2");
        assertThat(qualifier2.getOnlineNote())
            .isEqualTo("search policy: Online Manual; use: main heading/TQ2 or TQ2 (SH)");

        // 第三个限定词没有这两个字段，应该为 null
        MeshQualifierAggregate qualifier3 = qualifiers.get(2);
        assertThat(qualifier3.getHistoryNote()).isNull();
        assertThat(qualifier3.getOnlineNote()).isNull();
      }
    }

    @Test
    @DisplayName("解析限定词 - 应该正确解析 TreeNumberList")
    void parse_shouldParseTreeNumbers() throws IOException {
      // Given
      try (InputStream inputStream = openTestXmlStream()) {
        // When
        List<MeshQualifierAggregate> qualifiers;
        try (Stream<MeshQualifierAggregate> stream = parser.parse(inputStream)) {
          qualifiers = stream.toList();
        }

        // Then: 验证第一个限定词的树形编号（1 个）
        MeshQualifierAggregate qualifier1 = qualifiers.get(0);
        assertThat(qualifier1.getTreeNumbers()).hasSize(1);
        assertThat(qualifier1.getTreeNumbers()).containsExactly("Y01.001");

        // 验证第二个限定词的树形编号（2 个）
        MeshQualifierAggregate qualifier2 = qualifiers.get(1);
        assertThat(qualifier2.getTreeNumbers()).hasSize(2);
        assertThat(qualifier2.getTreeNumbers()).containsExactly("Y01.002", "Y02.001");

        // 第三个限定词没有树形编号，应该为空列表
        MeshQualifierAggregate qualifier3 = qualifiers.get(2);
        assertThat(qualifier3.getTreeNumbers()).isEmpty();
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
      try (InputStream inputStream = openTestXmlStream()) {
        // When: 打开并关闭 Stream
        Stream<MeshQualifierAggregate> stream = parser.parse(inputStream);
        stream.close();

        // Then: 不应该抛出异常
      }
    }

    @Test
    @DisplayName("try-with-resources - 应该正确释放资源")
    void tryWithResources_shouldReleaseResourcesCorrectly() throws IOException {
      // Given & When & Then: 使用 try-with-resources 自动关闭
      try (InputStream inputStream = openTestXmlStream();
          Stream<MeshQualifierAggregate> stream = parser.parse(inputStream)) {
        // 只读取第一个元素
        MeshQualifierAggregate first = stream.findFirst().orElse(null);
        assertThat(first).isNotNull();
        assertThat(first.getQualifierUi().ui()).isEqualTo("Q000001");
      }
      // 资源应该在这里被正确释放
    }
  }

  @Nested
  @DisplayName("异常场景测试")
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
                try (Stream<MeshQualifierAggregate> stream = parser.parse(invalidStream)) {
                  stream.findFirst(); // 触发实际解析
                }
              })
          .isInstanceOf(XmlParseException.class)
          .hasMessageContaining("XML 解析失败");
    }
  }
}
