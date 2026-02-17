package com.patra.catalog.infra.adapter.integration.wikidata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// WikidataSparqlClient 单元测试。
///
/// **测试策略**：
///
/// - 响应解析逻辑：验证 SPARQL JSON 响应的正确解析
/// - 中文标签优先级：zh > zh-hans > zh-hant
/// - 边界条件：空输入、空响应、畸形响应
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("WikidataSparqlClient 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class WikidataSparqlClientTest {

  @Mock private RestClient restClient;

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private WikidataSparqlClient client;

  @BeforeEach
  void setUp() {
    client = new WikidataSparqlClient(restClient, objectMapper);
  }

  @Nested
  @DisplayName("SPARQL 查询构建测试")
  class SparqlQueryBuildTest {

    @Test
    @DisplayName("应该正确构建包含多个 ISSN-L 的 VALUES 子句")
    void shouldBuildSparqlQueryWithMultipleIssnLs() {
      // Given
      Set<String> issnLs = Set.of("0028-0836", "0140-6736");

      // When
      String sparql = client.buildSparqlQuery(issnLs);

      // Then
      assertThat(sparql).contains("VALUES ?issnl");
      assertThat(sparql).contains("\"0028-0836\"");
      assertThat(sparql).contains("\"0140-6736\"");
      assertThat(sparql).contains("wdt:P7363");
      assertThat(sparql).contains("wdt:P236");
      assertThat(sparql).contains("FILTER(LANG(?label)");
    }

    @Test
    @DisplayName("应该正确构建单个 ISSN-L 的查询")
    void shouldBuildSparqlQueryWithSingleIssnL() {
      // Given
      Set<String> issnLs = Set.of("0028-0836");

      // When
      String sparql = client.buildSparqlQuery(issnLs);

      // Then
      assertThat(sparql).contains("\"0028-0836\"");
    }
  }

  @Nested
  @DisplayName("SPARQL 响应解析测试")
  class SparqlResponseParseTest {

    @Test
    @DisplayName("应该正确解析包含 zh 标签的响应")
    void shouldParseResponseWithZhLabel() {
      // Given
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"}
                }
              ]
            }
          }
          """;

      // When
      Map<String, String> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).containsExactly(Map.entry("0028-0836", "自然"));
    }

    @Test
    @DisplayName("应该正确解析多个 ISSN-L 的响应")
    void shouldParseResponseWithMultipleIssnLs() {
      // Given
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"}
                },
                {
                  "issnl": {"type": "literal", "value": "0140-6736"},
                  "label": {"type": "literal", "value": "柳叶刀", "xml:lang": "zh"}
                }
              ]
            }
          }
          """;

      // When
      Map<String, String> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result)
          .hasSize(2)
          .containsEntry("0028-0836", "自然")
          .containsEntry("0140-6736", "柳叶刀");
    }

    @Test
    @DisplayName("zh 标签应优先于 zh-hans")
    void shouldPreferZhOverZhHans() {
      // Given - 同一 ISSN-L 同时有 zh 和 zh-hans 标签
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然（简体）", "xml:lang": "zh-hans"}
                },
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"}
                }
              ]
            }
          }
          """;

      // When
      Map<String, String> result = client.parseSparqlResponse(json);

      // Then - 应选择 zh 标签
      assertThat(result).containsExactly(Map.entry("0028-0836", "自然"));
    }

    @Test
    @DisplayName("zh-hans 标签应优先于 zh-hant")
    void shouldPreferZhHansOverZhHant() {
      // Given - 只有 zh-hans 和 zh-hant 标签
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然（繁體）", "xml:lang": "zh-hant"}
                },
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然（简体）", "xml:lang": "zh-hans"}
                }
              ]
            }
          }
          """;

      // When
      Map<String, String> result = client.parseSparqlResponse(json);

      // Then - 应选择 zh-hans 标签
      assertThat(result).containsExactly(Map.entry("0028-0836", "自然（简体）"));
    }

    @Test
    @DisplayName("仅有 zh-hant 时应正确返回")
    void shouldReturnZhHantWhenOnlyOption() {
      // Given
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然（繁體）", "xml:lang": "zh-hant"}
                }
              ]
            }
          }
          """;

      // When
      Map<String, String> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).containsExactly(Map.entry("0028-0836", "自然（繁體）"));
    }

    @Test
    @DisplayName("空 bindings 应返回空 Map")
    void shouldReturnEmptyMapForEmptyBindings() {
      // Given
      String json =
          """
          {"results": {"bindings": []}}
          """;

      // When
      Map<String, String> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("畸形 JSON 应返回空 Map")
    void shouldReturnEmptyMapForMalformedJson() {
      // When
      Map<String, String> result = client.parseSparqlResponse("not json");

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 响应体应返回空 Map")
    void shouldReturnEmptyMapForNullResponseBody() {
      // When
      Map<String, String> result = client.parseSparqlResponse(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空白响应体应返回空 Map")
    void shouldReturnEmptyMapForBlankResponseBody() {
      // When
      Map<String, String> result = client.parseSparqlResponse("   ");

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("缺少 results 节点应返回空 Map")
    void shouldReturnEmptyMapForMissingResults() {
      // Given
      String json =
          """
          {"head": {"vars": ["issnl", "label"]}}
          """;

      // When
      Map<String, String> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("binding 缺少字段应被忽略")
    void shouldIgnoreIncompleteBindings() {
      // Given - 一个完整 binding 和一个缺少 label 的 binding
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"}
                },
                {
                  "issnl": {"type": "literal", "value": "0140-6736"}
                }
              ]
            }
          }
          """;

      // When
      Map<String, String> result = client.parseSparqlResponse(json);

      // Then - 只有完整的 binding 被解析
      assertThat(result).containsExactly(Map.entry("0028-0836", "自然"));
    }
  }

  @Nested
  @DisplayName("空输入测试")
  class EmptyInputTest {

    @Test
    @DisplayName("null 输入应返回空 Map")
    void shouldReturnEmptyMapForNullInput() {
      // When
      Map<String, String> result = client.queryChineseTitles(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空集合应返回空 Map")
    void shouldReturnEmptyMapForEmptySet() {
      // When
      Map<String, String> result = client.queryChineseTitles(Set.of());

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("全部非法 ISSN-L 格式应返回空 Map（防止 SPARQL 注入）")
    void shouldReturnEmptyMapWhenAllIssnLsAreInvalid() {
      // Given - 全部非法格式
      Set<String> issnLs = Set.of("invalid", "\" . } #", "12345");

      // When
      Map<String, String> result = client.queryChineseTitles(issnLs);

      // Then - 过滤后无有效 ISSN-L，直接返回空 Map（不发送请求）
      assertThat(result).isEmpty();
    }
  }
}
