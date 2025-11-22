package com.patra.starter.provenance.pubmed.request;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PubMedESearchRequestAssembler 单元测试
/// 
/// @author linqibin
@DisplayName("PubMedESearchRequestAssembler 测试")
class PubMedESearchRequestAssemblerTest {

  private PubMedESearchRequestAssembler assembler;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    assembler = new PubMedESearchRequestAssembler();
    objectMapper = new ObjectMapper();
  }

  // ========== buildCount 测试 ==========

  @Test
  @DisplayName("buildCount - 完整参数构建计数请求")
  void buildCount_shouldBuildCompleteCountRequest_withAllParams() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("term", "cancer");
    params.put("datetype", "pdat");
    params.put("mindate", "2020");
    params.put("maxdate", "2023");
    params.put("sort", "relevance");
    params.put("field", "title");
    params.put("reldate", "365");
    params.put("usehistory", "y");
    params.put("WebEnv", "NCID_123");
    params.put("query_key", "1");
    params.put("api_key", "test_api_key");
    params.put("tool", "patra");
    params.put("email", "admin@example.com");

    // Act
    ESearchRequest result = assembler.buildCount(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.term()).isEqualTo("cancer");
    assertThat(result.retmode()).isEqualTo("json");
    assertThat(result.rettype()).isEqualTo("count");
    assertThat(result.datetype()).isEqualTo("pdat");
    assertThat(result.mindate()).isEqualTo("2020");
    assertThat(result.maxdate()).isEqualTo("2023");
    assertThat(result.sort()).isEqualTo("relevance");
    assertThat(result.field()).isEqualTo("title");
    assertThat(result.reldate()).isEqualTo("365");
    assertThat(result.usehistory()).isEqualTo("y");
    assertThat(result.webenv()).isEqualTo("NCID_123");
    assertThat(result.queryKey()).isEqualTo("1");
    assertThat(result.apiKey()).isEqualTo("test_api_key");
    assertThat(result.tool()).isEqualTo("patra");
    assertThat(result.email()).isEqualTo("admin@example.com");
    // Count request忽略分页参数
    assertThat(result.retstart()).isNull();
    assertThat(result.retmax()).isNull();
  }

  @Test
  @DisplayName("buildCount - 仅term参数构建计数请求")
  void buildCount_shouldBuildCountRequest_withOnlyTerm() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("term", "diabetes");

    // Act
    ESearchRequest result = assembler.buildCount(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.term()).isEqualTo("diabetes");
    assertThat(result.retmode()).isEqualTo("json");
    assertThat(result.rettype()).isEqualTo("count");
    assertThat(result.retstart()).isNull();
    assertThat(result.retmax()).isNull();
    assertThat(result.sort()).isNull();
    assertThat(result.datetype()).isNull();
    assertThat(result.mindate()).isNull();
    assertThat(result.maxdate()).isNull();
  }

  @Test
  @DisplayName("buildCount - 无term但有日期过滤器")
  void buildCount_shouldBuildCountRequest_withDateFiltersButNoTerm() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("datetype", "pdat");
    params.put("mindate", "2022/01/01");
    params.put("maxdate", "2022/12/31");

    // Act
    ESearchRequest result = assembler.buildCount(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.term()).isNull(); // term可选
    assertThat(result.datetype()).isEqualTo("pdat");
    assertThat(result.mindate()).isEqualTo("2022/01/01");
    assertThat(result.maxdate()).isEqualTo("2022/12/31");
    assertThat(result.rettype()).isEqualTo("count");
  }

  @Test
  @DisplayName("buildCount - 空JsonNode构建基本计数请求")
  void buildCount_shouldBuildBasicCountRequest_whenParamsIsEmpty() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();

    // Act
    ESearchRequest result = assembler.buildCount(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.retmode()).isEqualTo("json");
    assertThat(result.rettype()).isEqualTo("count");
    assertThat(result.term()).isNull();
  }

  @Test
  @DisplayName("buildCount - null JsonNode构建基本计数请求")
  void buildCount_shouldBuildBasicCountRequest_whenParamsIsNull() {
    // Act
    ESearchRequest result = assembler.buildCount(null);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.retmode()).isEqualTo("json");
    assertThat(result.rettype()).isEqualTo("count");
  }

  @Test
  @DisplayName("buildCount - 自定义retmode")
  void buildCount_shouldUseCustomRetmode() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("retmode", "xml");

    // Act
    ESearchRequest result = assembler.buildCount(params);

    // Assert
    assertThat(result.retmode()).isEqualTo("xml");
  }

  // ========== buildList 测试 ==========

  @Test
  @DisplayName("buildList - 完整参数构建列表请求")
  void buildList_shouldBuildCompleteListRequest_withAllParams() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("term", "therapy");
    params.put("retstart", 0);
    params.put("retmax", 100);
    params.put("retmode", "json");
    params.put("rettype", "uilist");
    params.put("sort", "pub_date");
    params.put("datetype", "edat");
    params.put("mindate", "2021");
    params.put("maxdate", "2023");
    params.put("field", "abstract");
    params.put("reldate", "180");
    params.put("usehistory", "n");
    params.put("WebEnv", "ENV_456");
    params.put("query_key", "2");
    params.put("api_key", "my_key");
    params.put("tool", "test_tool");
    params.put("email", "test@example.com");

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.term()).isEqualTo("therapy");
    assertThat(result.retstart()).isEqualTo(0);
    assertThat(result.retmax()).isEqualTo(100);
    assertThat(result.retmode()).isEqualTo("json");
    assertThat(result.rettype()).isEqualTo("uilist");
    assertThat(result.sort()).isEqualTo("pub_date");
    assertThat(result.datetype()).isEqualTo("edat");
    assertThat(result.mindate()).isEqualTo("2021");
    assertThat(result.maxdate()).isEqualTo("2023");
    assertThat(result.field()).isEqualTo("abstract");
    assertThat(result.reldate()).isEqualTo("180");
    assertThat(result.usehistory()).isEqualTo("n");
    assertThat(result.webenv()).isEqualTo("ENV_456");
    assertThat(result.queryKey()).isEqualTo("2");
    assertThat(result.apiKey()).isEqualTo("my_key");
    assertThat(result.tool()).isEqualTo("test_tool");
    assertThat(result.email()).isEqualTo("test@example.com");
  }

  @Test
  @DisplayName("buildList - 仅分页参数构建列表请求")
  void buildList_shouldBuildListRequest_withOnlyPagination() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("retstart", 50);
    params.put("retmax", 20);

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.retstart()).isEqualTo(50);
    assertThat(result.retmax()).isEqualTo(20);
    assertThat(result.retmode()).isEqualTo("json");
    assertThat(result.rettype()).isNull(); // 默认为uilist
  }

  @Test
  @DisplayName("buildList - 空JsonNode构建基本列表请求")
  void buildList_shouldBuildBasicListRequest_whenParamsIsEmpty() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.retmode()).isEqualTo("json");
    assertThat(result.retstart()).isNull();
    assertThat(result.retmax()).isNull();
  }

  @Test
  @DisplayName("buildList - null JsonNode构建基本列表请求")
  void buildList_shouldBuildBasicListRequest_whenParamsIsNull() {
    // Act
    ESearchRequest result = assembler.buildList(null);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.db()).isEqualTo("pubmed");
    assertThat(result.retmode()).isEqualTo("json");
  }

  @Test
  @DisplayName("buildList - 无term但有日期过滤器")
  void buildList_shouldBuildListRequest_withDateFiltersButNoTerm() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("datetype", "mdat");
    params.put("mindate", "2023/06/01");
    params.put("maxdate", "2023/12/31");

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.term()).isNull(); // term可选
    assertThat(result.datetype()).isEqualTo("mdat");
    assertThat(result.mindate()).isEqualTo("2023/06/01");
    assertThat(result.maxdate()).isEqualTo("2023/12/31");
  }

  // ========== 参数提取测试 ==========

  @Test
  @DisplayName("extract - 整数参数正确解析")
  void extract_shouldParseIntegerParams_correctly() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("retstart", 10);
    params.put("retmax", 50);

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.retstart()).isEqualTo(10);
    assertThat(result.retmax()).isEqualTo(50);
  }

  @Test
  @DisplayName("extract - 字符串形式的整数正确解析")
  void extract_shouldParseStringInteger_correctly() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("retstart", "25");
    params.put("retmax", "75");

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.retstart()).isEqualTo(25);
    assertThat(result.retmax()).isEqualTo(75);
  }

  @Test
  @DisplayName("extract - 无效整数字符串返回null")
  void extract_shouldReturnNull_whenIntegerStringIsInvalid() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("retstart", "invalid");

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.retstart()).isNull();
  }

  @Test
  @DisplayName("extract - 支持WebEnv和webenv两种键")
  void extract_shouldSupportBothWebEnvCases() {
    // Arrange
    ObjectNode params1 = objectMapper.createObjectNode();
    params1.put("WebEnv", "ENV_PRIMARY");

    ObjectNode params2 = objectMapper.createObjectNode();
    params2.put("webenv", "ENV_FALLBACK");

    // Act
    ESearchRequest result1 = assembler.buildList(params1);
    ESearchRequest result2 = assembler.buildList(params2);

    // Assert
    assertThat(result1.webenv()).isEqualTo("ENV_PRIMARY");
    assertThat(result2.webenv()).isEqualTo("ENV_FALLBACK");
  }

  @Test
  @DisplayName("extract - WebEnv优先于webenv")
  void extract_shouldPreferWebEnv_overWebenv() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("WebEnv", "ENV_PRIMARY");
    params.put("webenv", "ENV_FALLBACK");

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.webenv()).isEqualTo("ENV_PRIMARY");
  }

  @Test
  @DisplayName("extract - 支持usehistory和usehistory两种键")
  void extract_shouldSupportBothUsehistoryCases() {
    // Arrange
    ObjectNode params1 = objectMapper.createObjectNode();
    params1.put("usehistory", "y");

    ObjectNode params2 = objectMapper.createObjectNode();
    params2.put("usehistory", "n");

    // Act
    ESearchRequest result1 = assembler.buildList(params1);
    ESearchRequest result2 = assembler.buildList(params2);

    // Assert
    assertThat(result1.usehistory()).isEqualTo("y");
    assertThat(result2.usehistory()).isEqualTo("n");
  }

  @Test
  @DisplayName("extract - 支持query_key和queryKey两种键")
  void extract_shouldSupportBothQueryKeyCases() {
    // Arrange
    ObjectNode params1 = objectMapper.createObjectNode();
    params1.put("query_key", "key1");

    ObjectNode params2 = objectMapper.createObjectNode();
    params2.put("queryKey", "key2");

    // Act
    ESearchRequest result1 = assembler.buildList(params1);
    ESearchRequest result2 = assembler.buildList(params2);

    // Assert
    assertThat(result1.queryKey()).isEqualTo("key1");
    assertThat(result2.queryKey()).isEqualTo("key2");
  }

  @Test
  @DisplayName("extract - 支持api_key和apiKey两种键")
  void extract_shouldSupportBothApiKeyCases() {
    // Arrange
    ObjectNode params1 = objectMapper.createObjectNode();
    params1.put("api_key", "key_primary");

    ObjectNode params2 = objectMapper.createObjectNode();
    params2.put("apiKey", "key_fallback");

    // Act
    ESearchRequest result1 = assembler.buildList(params1);
    ESearchRequest result2 = assembler.buildList(params2);

    // Assert
    assertThat(result1.apiKey()).isEqualTo("key_primary");
    assertThat(result2.apiKey()).isEqualTo("key_fallback");
  }

  @Test
  @DisplayName("extract - null值参数正确处理")
  void extract_shouldHandleNullValues() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("term", "test");
    params.putNull("sort");
    params.putNull("retstart");

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.term()).isEqualTo("test");
    assertThat(result.sort()).isNull();
    assertThat(result.retstart()).isNull();
  }

  @Test
  @DisplayName("extract - 零值整数正确处理")
  void extract_shouldHandleZeroIntegerValues() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("retstart", 0);
    params.put("retmax", 0);

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.retstart()).isEqualTo(0);
    assertThat(result.retmax()).isEqualTo(0);
  }

  @Test
  @DisplayName("extract - 负数整数正确处理")
  void extract_shouldHandleNegativeIntegerValues() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("retstart", -10);

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.retstart()).isEqualTo(-10);
  }

  @Test
  @DisplayName("extract - 所有字符串参数正确提取")
  void extract_shouldExtractAllStringParams() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("term", "query text");
    params.put("retmode", "xml");
    params.put("rettype", "count");
    params.put("sort", "relevance");
    params.put("datetype", "pdat");
    params.put("mindate", "2020");
    params.put("maxdate", "2024");
    params.put("field", "author");
    params.put("reldate", "90");
    params.put("tool", "my_tool");
    params.put("email", "user@example.com");

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.term()).isEqualTo("query text");
    assertThat(result.retmode()).isEqualTo("xml");
    assertThat(result.rettype()).isEqualTo("count");
    assertThat(result.sort()).isEqualTo("relevance");
    assertThat(result.datetype()).isEqualTo("pdat");
    assertThat(result.mindate()).isEqualTo("2020");
    assertThat(result.maxdate()).isEqualTo("2024");
    assertThat(result.field()).isEqualTo("author");
    assertThat(result.reldate()).isEqualTo("90");
    assertThat(result.tool()).isEqualTo("my_tool");
    assertThat(result.email()).isEqualTo("user@example.com");
  }

  @Test
  @DisplayName("extract - 包含特殊字符的查询正确解析")
  void extract_shouldParseQueryWithSpecialCharacters() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.put("term", "cancer AND (therapy OR treatment) NOT \"old therapy\"");

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.term()).isEqualTo("cancer AND (therapy OR treatment) NOT \"old therapy\"");
  }

  @Test
  @DisplayName("buildList - retmode默认为json")
  void buildList_shouldDefaultToJsonRetmode() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    // 不设置retmode

    // Act
    ESearchRequest result = assembler.buildList(params);

    // Assert
    assertThat(result.retmode()).isEqualTo("json");
  }

  @Test
  @DisplayName("buildCount - retmode为null时默认为json")
  void buildCount_shouldDefaultToJsonRetmode_whenRepmodeIsNull() {
    // Arrange
    ObjectNode params = objectMapper.createObjectNode();
    params.putNull("retmode");

    // Act
    ESearchRequest result = assembler.buildCount(params);

    // Assert
    assertThat(result.retmode()).isEqualTo("json");
  }
}
