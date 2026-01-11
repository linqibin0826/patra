package com.patra.catalog.infra.adapter.batch.author;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.model.enums.AuthorStatus;
import com.patra.catalog.domain.model.enums.DataSourceCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.json.JsonMapper;

/// PubMedComputedAuthorParser 单元测试。
///
/// **测试覆盖**：
///
/// - 正常解析：完整格式、多变体、多 ORCID
/// - 边界情况：空行、无 ORCID、无 names
/// - 错误处理：格式错误的 JSON、无效 ORCID
/// - 中文姓名支持
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubMedComputedAuthorParser 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PubMedComputedAuthorParserTest {

  private PubMedComputedAuthorParser parser;

  @BeforeEach
  void setUp() {
    parser = new PubMedComputedAuthorParser(JsonMapper.builder().build());
  }

  @Nested
  @DisplayName("正常解析场景")
  class NormalParsingTests {

    @Test
    @DisplayName("解析完整格式 - 应该正确提取所有字段")
    void parse_fullFormat_shouldExtractAllFields() throws IOException {
      // Given: 完整格式的 JSON
      String jsonLines =
          """
          {"name": "LU+Z", "names": ["Lu,Zhiyong,Z"], "orcid": ["0000-0001-5109-3700"], "pmids": [32708434]}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);

      AuthorAggregate author = authors.getFirst();
      assertThat(author.getNormalizedKey()).isEqualTo("LU+Z");
      assertThat(author.getProvenanceCode()).isEqualTo(DataSourceCode.PUBMED);
      assertThat(author.getStatus()).isEqualTo(AuthorStatus.ACTIVE);

      // 验证名字变体
      assertThat(author.getNameVariants()).hasSize(1);
      assertThat(author.getNameVariants().getFirst().lastName()).isEqualTo("Lu");
      assertThat(author.getNameVariants().getFirst().foreName()).isEqualTo("Zhiyong");
      assertThat(author.getNameVariants().getFirst().initials()).isEqualTo("Z");

      // 验证 ORCID
      assertThat(author.getOrcids()).hasSize(1);
      assertThat(author.getOrcids().getFirst().value()).isEqualTo("0000-0001-5109-3700");

      // 验证 displayName
      assertThat(author.getDisplayName()).isEqualTo("Zhiyong Lu");
    }

    @Test
    @DisplayName("解析多个作者 - 应该返回所有作者")
    void parse_multipleAuthors_shouldReturnAll() throws IOException {
      // Given: 多行 JSON
      String jsonLines =
          """
          {"name": "SMITH+J", "names": ["Smith,John,J"], "orcid": [], "pmids": []}
          {"name": "BROWN+A", "names": ["Brown,Alice,A"], "orcid": ["0000-0002-1825-0097"], "pmids": []}
          {"name": "WILSON+B", "names": ["Wilson,Bob,B"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(3);
      assertThat(authors)
          .extracting(AuthorAggregate::getNormalizedKey)
          .containsExactly("SMITH+J", "BROWN+A", "WILSON+B");
    }

    @Test
    @DisplayName("解析多名字变体 - 应该保留所有变体")
    void parse_multipleNameVariants_shouldRetainAll() throws IOException {
      // Given: 有多个名字变体的作者
      String jsonLines =
          """
          {"name": "WANG+XM", "names": ["Wang,Xiaoming,XM", "Wang,XM", "王,小明"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getNameVariants()).hasSize(3);
    }

    @Test
    @DisplayName("解析多 ORCID - 应该保留所有 ORCID")
    void parse_multipleOrcids_shouldRetainAll() throws IOException {
      // Given: 有多个 ORCID 的作者（极少数情况）
      String jsonLines =
          """
          {"name": "JOHNSON+MK", "names": ["Johnson,Mary,MK"], "orcid": ["0000-0001-5109-3700", "0000-0002-1825-0097"], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getOrcids()).hasSize(2);
    }

    @Test
    @DisplayName("解析 PubMed 简化格式 - 姓氏和缩写")
    void parse_pubMedShortFormat_lastNameAndInitials() throws IOException {
      // Given: PubMed 常见的简化格式
      String jsonLines =
          """
          {"name": "SMITH+JK", "names": ["Smith,JK"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);
      var variant = authors.getFirst().getNameVariants().getFirst();
      assertThat(variant.lastName()).isEqualTo("Smith");
      assertThat(variant.initials()).isEqualTo("JK");
      assertThat(variant.foreName()).isNull();
    }
  }

  @Nested
  @DisplayName("边界情况")
  class EdgeCaseTests {

    @Test
    @DisplayName("空行 - 应该跳过")
    void parse_blankLines_shouldSkip() throws IOException {
      // Given: 包含空行的 JSON Lines
      String jsonLines =
          """

          {"name": "SMITH+J", "names": ["Smith,John,J"], "orcid": [], "pmids": []}

          {"name": "BROWN+A", "names": ["Brown,Alice,A"], "orcid": [], "pmids": []}

          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(2);
    }

    @Test
    @DisplayName("无 ORCID 字段 - 应该正常解析")
    void parse_noOrcidField_shouldParseNormally() throws IOException {
      // Given: 没有 ORCID 字段
      String jsonLines =
          """
          {"name": "SMITH+J", "names": ["Smith,John,J"], "pmids": [32708434]}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getOrcids()).isEmpty();
    }

    @Test
    @DisplayName("空 ORCID 数组 - 应该正常解析")
    void parse_emptyOrcidArray_shouldParseNormally() throws IOException {
      // Given: 空 ORCID 数组
      String jsonLines =
          """
          {"name": "SMITH+J", "names": ["Smith,John,J"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getOrcids()).isEmpty();
      assertThat(authors.getFirst().hasOrcid()).isFalse();
    }

    @Test
    @DisplayName("空 names 数组 - 应该正常解析但无变体")
    void parse_emptyNamesArray_shouldParseWithoutVariants() throws IOException {
      // Given: 空 names 数组
      String jsonLines =
          """
          {"name": "UNKNOWN+X", "names": [], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getNameVariants()).isEmpty();
      assertThat(authors.getFirst().getDisplayName()).isNull();
    }
  }

  @Nested
  @DisplayName("错误处理")
  class ErrorHandlingTests {

    @Test
    @DisplayName("格式错误的 JSON - 应该跳过并继续")
    void parse_malformedJson_shouldSkipAndContinue() throws IOException {
      // Given: 包含格式错误 JSON 的输入
      String jsonLines =
          """
          {"name": "SMITH+J", "names": ["Smith,John,J"], "orcid": [], "pmids": []}
          {invalid json}
          {"name": "BROWN+A", "names": ["Brown,Alice,A"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then: 跳过错误行，继续处理其他记录
      assertThat(authors).hasSize(2);
    }

    @Test
    @DisplayName("无效 ORCID 格式 - 应该跳过该 ORCID")
    void parse_invalidOrcidFormat_shouldSkipOrcid() throws IOException {
      // Given: 无效的 ORCID 格式
      String jsonLines =
          """
          {"name": "SMITH+J", "names": ["Smith,John,J"], "orcid": ["invalid-orcid", "0000-0001-5109-3700"], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then: 应该只保留有效的 ORCID
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getOrcids()).hasSize(1);
      assertThat(authors.getFirst().getOrcids().getFirst().value())
          .isEqualTo("0000-0001-5109-3700");
    }
  }

  @Nested
  @DisplayName("中文姓名支持")
  class ChineseNameTests {

    @Test
    @DisplayName("中文姓名变体 - 应该正确解析 UTF-8")
    void parse_chineseName_shouldHandleUtf8() throws IOException {
      // Given: 中文姓名
      String jsonLines =
          """
          {"name": "WANG+XM", "names": ["Wang,Xiaoming,XM", "王,小明"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getNameVariants()).hasSize(2);

      // 验证中文姓名被正确解析
      boolean hasChineseVariant =
          authors.getFirst().getNameVariants().stream().anyMatch(v -> "王".equals(v.lastName()));
      assertThat(hasChineseVariant).isTrue();
    }

    @Test
    @DisplayName("纯中文姓名 - 应该正确解析")
    void parse_pureChineseName_shouldParse() throws IOException {
      // Given: 纯中文姓名（单部分格式）
      String jsonLines =
          """
          {"name": "李明+", "names": ["李明"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then
      assertThat(authors).hasSize(1);
      var variant = authors.getFirst().getNameVariants().getFirst();
      assertThat(variant.lastName()).isEqualTo("李明");
    }
  }

  @Nested
  @DisplayName("名字变体去重（ICU4J Collation）")
  class NameVariantDeduplicationTests {

    @Test
    @DisplayName("大小写不同 - 应该去重为一个变体")
    void parse_caseVariants_shouldDeduplicate() throws IOException {
      // Given: 同一名字的大小写变体（如 PubMed 数据中的 Tephly/TEPHLY）
      String jsonLines =
          """
          {"name": "TEPHLY+T", "names": ["Tephly,T R,TR", "TEPHLY,T R,TR"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then: 应该只保留第一个变体
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getNameVariants()).hasSize(1);
      assertThat(authors.getFirst().getNameVariants().getFirst().lastName()).isEqualTo("Tephly");
    }

    @Test
    @DisplayName("重音字符差异 - 应该去重（García = Garcia）")
    void parse_accentVariants_shouldDeduplicate() throws IOException {
      // Given: 带重音和不带重音的变体（MySQL utf8mb4_0900_ai_ci 视为相同）
      String jsonLines =
          """
          {"name": "GARCIA+M", "names": ["García,María,M", "Garcia,Maria,M"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then: 应该只保留第一个变体（带重音的）
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getNameVariants()).hasSize(1);
      assertThat(authors.getFirst().getNameVariants().getFirst().lastName()).isEqualTo("García");
    }

    @Test
    @DisplayName("德语变音差异 - 应该去重（Müller = Muller，忽略变音符号）")
    void parse_germanUmlautVariants_shouldDeduplicate() throws IOException {
      // Given: 德语变音字符变体（ü → u，变音符号被忽略）
      // 注意：Müller ≠ Mueller（后者是两个字符 ue，不是变音符号）
      String jsonLines =
          """
          {"name": "MULLER+H", "names": ["Müller,Hans,H", "Muller,Hans,H"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then: 应该只保留第一个变体（带变音符号的）
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getNameVariants()).hasSize(1);
      assertThat(authors.getFirst().getNameVariants().getFirst().lastName()).isEqualTo("Müller");
    }

    @Test
    @DisplayName("德语 ue 替代写法 - 应该保留两个变体（Müller ≠ Mueller）")
    void parse_germanUeAlternative_shouldRetainBoth() throws IOException {
      // Given: Müller 和 Mueller 是不同的字符序列
      // Müller: M-ü-l-l-e-r (6 chars, ü 是单个变音字符)
      // Mueller: M-u-e-l-l-e-r (7 chars, ue 是两个字符)
      String jsonLines =
          """
          {"name": "MULLER+H", "names": ["Müller,Hans,H", "Mueller,Hans,H"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then: 两者是不同的变体，都应该保留
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getNameVariants()).hasSize(2);
    }

    @Test
    @DisplayName("不同名字 - 应该保留所有变体")
    void parse_differentNames_shouldRetainAll() throws IOException {
      // Given: 真正不同的名字变体
      String jsonLines =
          """
          {"name": "SMITH+J", "names": ["Smith,John,J", "Smith,James,J", "Smith,Jack,J"], "orcid": [], "pmids": []}
          """;

      // When
      List<AuthorAggregate> authors = parseToList(jsonLines);

      // Then: 所有不同的变体都应该保留
      assertThat(authors).hasSize(1);
      assertThat(authors.getFirst().getNameVariants()).hasSize(3);
    }
  }

  @Nested
  @DisplayName("流式处理测试")
  class StreamProcessingTests {

    @Test
    @DisplayName("Stream 关闭 - 应该正确释放资源")
    void parse_streamClose_shouldReleaseResources() throws IOException {
      // Given
      String jsonLines =
          """
          {"name": "SMITH+J", "names": ["Smith,John,J"], "orcid": [], "pmids": []}
          """;
      InputStream inputStream = toInputStream(jsonLines);

      // When
      Stream<AuthorAggregate> stream = parser.parse(inputStream);

      // Then: 消费流并关闭
      List<AuthorAggregate> authors = stream.toList();
      stream.close();

      assertThat(authors).hasSize(1);
      // 资源应该被正确释放（无异常抛出即可）
    }

    @Test
    @DisplayName("大批量数据 - 应该正确流式处理")
    void parse_largeData_shouldStreamProcess() throws IOException {
      // Given: 生成 500 条测试数据
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(500, baos);
      InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

      // When
      try (Stream<AuthorAggregate> stream = parser.parse(inputStream)) {
        List<AuthorAggregate> authors = stream.toList();

        // Then
        assertThat(authors).hasSize(500);
        assertThat(authors)
            .allMatch(a -> a.getNormalizedKey() != null)
            .allMatch(a -> a.getProvenanceCode() == DataSourceCode.PUBMED)
            .allMatch(a -> a.getStatus() == AuthorStatus.ACTIVE);
      }
    }
  }

  // ========== 辅助方法 ==========

  private List<AuthorAggregate> parseToList(String jsonLines) throws IOException {
    try (Stream<AuthorAggregate> stream = parser.parse(toInputStream(jsonLines))) {
      return stream.toList();
    }
  }

  private InputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
