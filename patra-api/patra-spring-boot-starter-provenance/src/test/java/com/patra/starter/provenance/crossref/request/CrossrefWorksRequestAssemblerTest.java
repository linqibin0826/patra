package com.patra.starter.provenance.crossref.request;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.starter.provenance.crossref.model.request.CrossrefWorksRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CrossrefWorksRequestAssembler 单元测试
 *
 * @author linqibin
 */
@DisplayName("CrossrefWorksRequestAssembler 测试")
class CrossrefWorksRequestAssemblerTest {

  private CrossrefWorksRequestAssembler assembler;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    assembler = new CrossrefWorksRequestAssembler();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("build - 完整参数构建请求")
  void build_shouldBuildCompleteRequest_withAllParams() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "cancer research");
    params.put("filter", "type:journal-article");
    params.put("rows", 100);
    params.put("offset", 50);
    params.put("sort", "published");
    params.put("order", "desc");
    params.put("cursor", "cursorMark123");
    params.put("select", "DOI,title,author");
    params.put("mailto", "admin@example.com");

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.query()).isEqualTo("cancer research");
    assertThat(result.filter()).isEqualTo("type:journal-article");
    assertThat(result.rows()).isEqualTo(100);
    assertThat(result.offset()).isEqualTo(50);
    assertThat(result.sort()).isEqualTo("published");
    assertThat(result.order()).isEqualTo("desc");
    assertThat(result.cursor()).isEqualTo("cursorMark123");
    assertThat(result.select()).isEqualTo("DOI,title,author");
    assertThat(result.mailto()).isEqualTo("admin@example.com");
  }

  @Test
  @DisplayName("build - 仅必填参数构建请求")
  void build_shouldBuildRequest_withOnlyQuery() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "diabetes");

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.query()).isEqualTo("diabetes");
    assertThat(result.filter()).isNull();
    assertThat(result.rows()).isNull();
    assertThat(result.offset()).isNull();
    assertThat(result.sort()).isNull();
    assertThat(result.order()).isNull();
    assertThat(result.cursor()).isNull();
    assertThat(result.select()).isNull();
    assertThat(result.mailto()).isNull();
  }

  @Test
  @DisplayName("build - 空JsonNode返回全null请求")
  void build_shouldBuildRequestWithNullFields_whenParamsIsEmpty() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "test"); // 至少需要提供 query 或 filter

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.query()).isEqualTo("test");
    assertThat(result.filter()).isNull();
    assertThat(result.rows()).isNull();
    assertThat(result.offset()).isNull();
  }

  @Test
  @DisplayName("build - null JsonNode抛出异常")
  void build_shouldBuildRequestWithNullFields_whenParamsIsNull() {
    // Act & Assert - null params 应该提供默认 query 以满足校验
    // 由于 null 无法处理,assembler 应该抛出异常或提供默认值
    // 这里我们测试如果 assembler 内部处理 null 的情况
    assertThatThrownBy(() -> assembler.build(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Crossref 请求必须提供 'query' 或 'filter' 中的至少一个");
  }

  @Test
  @DisplayName("build - 整数参数正确解析")
  void build_shouldParseIntegerParams_correctly() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "test"); // 至少需要提供 query 或 filter
    params.put("rows", 25);
    params.put("offset", 100);

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result.rows()).isEqualTo(25);
    assertThat(result.offset()).isEqualTo(100);
  }

  @Test
  @DisplayName("build - 字符串形式的整数正确解析")
  void build_shouldParseStringInteger_correctly() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "test"); // 至少需要提供 query 或 filter
    params.put("rows", "50");
    params.put("offset", "200");

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result.rows()).isEqualTo(50);
    assertThat(result.offset()).isEqualTo(200);
  }

  @Test
  @DisplayName("build - 长整型转换为整型")
  void build_shouldConvertLongToInteger() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "test"); // 至少需要提供 query 或 filter
    params.put("rows", 100L);

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result.rows()).isEqualTo(100);
  }

  @Test
  @DisplayName("build - 超出整数范围的长整型抛出异常")
  void build_shouldThrowException_whenLongExceedsIntegerRange() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("rows", Long.MAX_VALUE);

    // Act & Assert
    assertThatThrownBy(() -> assembler.build(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("超出整数范围");
  }

  @Test
  @DisplayName("build - 无效的整数字符串抛出异常")
  void build_shouldThrowException_whenIntegerStringIsInvalid() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("rows", "not-a-number");

    // Act & Assert
    assertThatThrownBy(() -> assembler.build(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不是有效的整数");
  }

  @Test
  @DisplayName("build - 空白字符串被视为null并抛出异常")
  void build_shouldTreatBlankStringAsNull() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "   ");
    params.put("filter", "");

    // Act & Assert - 空白字符串被视为 null,因此不满足至少提供一个的要求
    assertThatThrownBy(() -> assembler.build(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Crossref 请求必须提供 'query' 或 'filter' 中的至少一个");
  }

  @Test
  @DisplayName("build - null值正确处理")
  void build_shouldHandleNullValues() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "test");
    params.putNull("filter");
    params.putNull("rows");

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result.query()).isEqualTo("test");
    assertThat(result.filter()).isNull();
    assertThat(result.rows()).isNull();
  }

  @Test
  @DisplayName("build - 包含特殊字符的查询正确解析")
  void build_shouldParseQueryWithSpecialCharacters() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "cancer AND therapy OR \"breast cancer\"");
    params.put("filter", "from-pub-date:2020-01,until-pub-date:2023-12");

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result.query()).isEqualTo("cancer AND therapy OR \"breast cancer\"");
    assertThat(result.filter()).isEqualTo("from-pub-date:2020-01,until-pub-date:2023-12");
  }

  @Test
  @DisplayName("build - 零值整数应抛出异常")
  void build_shouldHandleZeroIntegerValues() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "test"); // 至少需要提供 query 或 filter
    params.put("rows", 0);
    params.put("offset", 0);

    // Act & Assert - rows=0 不满足 "必须为正数" 的要求
    assertThatThrownBy(() -> assembler.build(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'rows' 参数必须为正数");
  }

  @Test
  @DisplayName("build - 负数整数应抛出异常")
  void build_shouldHandleNegativeIntegerValues() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "test"); // 至少需要提供 query 或 filter
    params.put("rows", -10);
    params.put("offset", -5);

    // Act & Assert - 负数不满足参数校验要求
    assertThatThrownBy(() -> assembler.build(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("参数必须");
  }

  @Test
  @DisplayName("build - 混合类型参数正确处理")
  void build_shouldHandleMixedTypeParams() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("query", "test");
    params.put("rows", 10);
    params.put("offset", "20");
    params.put("mailto", "test@example.com");
    params.putNull("filter");

    // Act
    CrossrefWorksRequest result = assembler.build(params);

    // Assert
    assertThat(result.query()).isEqualTo("test");
    assertThat(result.rows()).isEqualTo(10);
    assertThat(result.offset()).isEqualTo(20);
    assertThat(result.mailto()).isEqualTo("test@example.com");
    assertThat(result.filter()).isNull();
  }
}
