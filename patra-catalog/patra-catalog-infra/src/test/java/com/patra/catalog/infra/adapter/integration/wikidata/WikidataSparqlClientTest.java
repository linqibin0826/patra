package com.patra.catalog.infra.adapter.integration.wikidata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.patra.catalog.domain.model.vo.venue.VenueWikidataEnrichment;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// WikidataSparqlClient 单元测试。
///
/// **测试策略**：
///
/// - 响应解析逻辑：验证 SPARQL JSON 响应的正确解析（中文标题 + 封面图片 + 官方网站）
/// - 中文标签优先级：zh > zh-hans > zh-hant
/// - 封面图片 URL 解析：P18 字段映射到 imageUrl
/// - 官方网站 URL 解析：P856 字段映射到 homepageUrl
/// - 聚合逻辑：同一 ISSN-L 多行（笛卡尔积）正确合并
/// - 边界条件：空输入、空响应、畸形响应
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("WikidataSparqlClient 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class WikidataSparqlClientTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private WikidataSparqlClient client;

  @BeforeEach
  void setUp() {
    // RestClient 仅在 queryEnrichmentData() 的 HTTP 调用中使用，
    // 本测试类聚焦解析和构建逻辑，传入 null 即可
    client = new WikidataSparqlClient(null, objectMapper);
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
      assertThat(sparql).contains("wdt:P18");
      assertThat(sparql).contains("wdt:P856");
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
  @DisplayName("SPARQL 响应解析测试 - 中文标题")
  class SparqlResponseParseChineseTitleTest {

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
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
      assertThat(result.get("0028-0836").imageUrl()).isNull();
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
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
      assertThat(result.get("0140-6736").titleZh()).isEqualTo("柳叶刀");
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
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then - 应选择 zh 标签
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
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
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then - 应选择 zh-hans 标签
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然（简体）");
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
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然（繁體）");
    }
  }

  @Nested
  @DisplayName("SPARQL 响应解析测试 - 封面图片")
  class SparqlResponseParseImageTest {

    private static final String IMAGE_URL =
        "http://commons.wikimedia.org/wiki/Special:FilePath/Nature_magazine.jpg";

    @Test
    @DisplayName("应该正确解析包含封面图片 URL 的响应")
    void shouldParseResponseWithImageUrl() {
      // Given
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"},
                  "image": {"type": "uri", "value": "%s"}
                }
              ]
            }
          }
          """
              .formatted(IMAGE_URL);

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
      assertThat(result.get("0028-0836").imageUrl()).isEqualTo(IMAGE_URL);
    }

    @Test
    @DisplayName("仅有封面图片无中文标题时应正确返回")
    void shouldReturnImageUrlWithoutChineseTitle() {
      // Given - 有 P18 图片但无中文标签（如非中文期刊但有封面）
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "image": {"type": "uri", "value": "%s"}
                }
              ]
            }
          }
          """
              .formatted(IMAGE_URL);

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("0028-0836").titleZh()).isNull();
      assertThat(result.get("0028-0836").imageUrl()).isEqualTo(IMAGE_URL);
    }

    @Test
    @DisplayName("同一 ISSN-L 多行（笛卡尔积）时只取第一张图片")
    void shouldTakeFirstImageUrlFromMultipleRows() {
      // Given - 多语言标签 × 图片 URL 产生笛卡尔积，图片应只取第一个
      String image1 = "http://commons.wikimedia.org/wiki/Special:FilePath/Nature_v1.jpg";
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然（简体）", "xml:lang": "zh-hans"},
                  "image": {"type": "uri", "value": "%s"}
                },
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"},
                  "image": {"type": "uri", "value": "%s"}
                }
              ]
            }
          }
          """
              .formatted(image1, IMAGE_URL);

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then - zh 标签优先，图片取第一个出现的
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
      assertThat(result.get("0028-0836").imageUrl()).isEqualTo(image1);
    }
  }

  @Nested
  @DisplayName("SPARQL 响应解析测试 - 官方网站")
  class SparqlResponseParseHomepageTest {

    private static final String HOMEPAGE_URL = "https://www.nature.com/nm";

    @Test
    @DisplayName("应该正确解析包含官方网站 URL 的响应")
    void shouldParseResponseWithHomepageUrl() {
      // Given
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"},
                  "homepage": {"type": "uri", "value": "%s"}
                }
              ]
            }
          }
          """
              .formatted(HOMEPAGE_URL);

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
      assertThat(result.get("0028-0836").homepageUrl()).isEqualTo(HOMEPAGE_URL);
    }

    @Test
    @DisplayName("仅有官方网站无其他富化数据时应正确返回")
    void shouldReturnHomepageUrlOnly() {
      // Given
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "homepage": {"type": "uri", "value": "%s"}
                }
              ]
            }
          }
          """
              .formatted(HOMEPAGE_URL);

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("0028-0836").titleZh()).isNull();
      assertThat(result.get("0028-0836").imageUrl()).isNull();
      assertThat(result.get("0028-0836").homepageUrl()).isEqualTo(HOMEPAGE_URL);
    }

    @Test
    @DisplayName("同一 ISSN-L 多行时只取第一个官方网站 URL")
    void shouldTakeFirstHomepageUrlFromMultipleRows() {
      // Given
      String homepage1 = "https://www.nature.com";
      String homepage2 = "https://www.nature.com/nm";
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"},
                  "homepage": {"type": "uri", "value": "%s"}
                },
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然（简体）", "xml:lang": "zh-hans"},
                  "homepage": {"type": "uri", "value": "%s"}
                }
              ]
            }
          }
          """
              .formatted(homepage1, homepage2);

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then — 取第一个出现的（putIfAbsent 语义）
      assertThat(result.get("0028-0836").homepageUrl()).isEqualTo(homepage1);
    }

    @Test
    @DisplayName("三种富化数据（中文标题 + 封面图片 + 官方网站）应全部正确解析")
    void shouldParseAllThreeEnrichmentFields() {
      // Given
      String imageUrl = "http://commons.wikimedia.org/wiki/Special:FilePath/Nature_magazine.jpg";
      String json =
          """
          {
            "results": {
              "bindings": [
                {
                  "issnl": {"type": "literal", "value": "0028-0836"},
                  "label": {"type": "literal", "value": "自然", "xml:lang": "zh"},
                  "image": {"type": "uri", "value": "%s"},
                  "homepage": {"type": "uri", "value": "%s"}
                }
              ]
            }
          }
          """
              .formatted(imageUrl, HOMEPAGE_URL);

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      VenueWikidataEnrichment enrichment = result.get("0028-0836");
      assertThat(enrichment.titleZh()).isEqualTo("自然");
      assertThat(enrichment.imageUrl()).isEqualTo(imageUrl);
      assertThat(enrichment.homepageUrl()).isEqualTo(HOMEPAGE_URL);
    }
  }

  @Nested
  @DisplayName("SPARQL 响应解析测试 - 边界条件")
  class SparqlResponseParseBoundaryTest {

    @Test
    @DisplayName("空 bindings 应返回空 Map")
    void shouldReturnEmptyMapForEmptyBindings() {
      // Given
      String json =
          """
          {"results": {"bindings": []}}
          """;

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("畸形 JSON 应返回空 Map")
    void shouldReturnEmptyMapForMalformedJson() {
      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse("not json");

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 响应体应返回空 Map")
    void shouldReturnEmptyMapForNullResponseBody() {
      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空白响应体应返回空 Map")
    void shouldReturnEmptyMapForBlankResponseBody() {
      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse("   ");

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("缺少 results 节点应返回空 Map")
    void shouldReturnEmptyMapForMissingResults() {
      // Given
      String json =
          """
          {"head": {"vars": ["issnl", "label", "image"]}}
          """;

      // When
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("binding 既无 label/image/homepage 时被 FILTER 过滤，不应出现在结果中")
    void shouldIgnoreBindingsWithoutLabelOrImage() {
      // Given - 一个有富化数据，一个没有（由 SPARQL FILTER 过滤，测试解析器健壮性）
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
      Map<String, VenueWikidataEnrichment> result = client.parseSparqlResponse(json);

      // Then - 无富化字段的行不进入结果
      assertThat(result).hasSize(1);
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
      assertThat(result).doesNotContainKey("0140-6736");
    }
  }

  @Nested
  @DisplayName("重试机制测试")
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  class RetryTest {

    private static final String VALID_RESPONSE =
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

    @Test
    @DisplayName("首次失败后重试成功应返回正确数据")
    void shouldReturnDataAfterRetrySuccess() {
      // Given — 使用极短延迟的测试构造器
      WikidataSparqlClient spyClient =
          spy(new WikidataSparqlClient(null, objectMapper, 2, Duration.ofMillis(10)));

      // 第 1 次抛异常，第 2 次返回成功
      doThrow(new ResourceAccessException("I/O error: get"))
          .doReturn(VALID_RESPONSE)
          .when(spyClient)
          .executeSparqlQuery(any());

      // When
      Map<String, VenueWikidataEnrichment> result =
          spyClient.queryEnrichmentData(Set.of("0028-0836"));

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
      verify(spyClient, times(2)).executeSparqlQuery(any());
    }

    @Test
    @DisplayName("所有重试都失败应返回空 Map")
    void shouldReturnEmptyMapAfterAllRetriesExhausted() {
      // Given
      WikidataSparqlClient spyClient =
          spy(new WikidataSparqlClient(null, objectMapper, 2, Duration.ofMillis(10)));

      // 3 次全部失败（1 次初始 + 2 次重试）
      doThrow(new ResourceAccessException("I/O error: get"))
          .when(spyClient)
          .executeSparqlQuery(any());

      // When
      Map<String, VenueWikidataEnrichment> result =
          spyClient.queryEnrichmentData(Set.of("0028-0836"));

      // Then — 优雅降级，返回空 Map
      assertThat(result).isEmpty();
      verify(spyClient, times(3)).executeSparqlQuery(any());
    }

    @Test
    @DisplayName("首次成功不应触发重试")
    void shouldNotRetryOnFirstSuccess() {
      // Given
      WikidataSparqlClient spyClient =
          spy(new WikidataSparqlClient(null, objectMapper, 2, Duration.ofMillis(10)));

      doReturn(VALID_RESPONSE).when(spyClient).executeSparqlQuery(any());

      // When
      Map<String, VenueWikidataEnrichment> result =
          spyClient.queryEnrichmentData(Set.of("0028-0836"));

      // Then
      assertThat(result).hasSize(1);
      verify(spyClient, times(1)).executeSparqlQuery(any());
    }
  }

  @Nested
  @DisplayName("空输入测试")
  class EmptyInputTest {

    @Test
    @DisplayName("null 输入应返回空 Map")
    void shouldReturnEmptyMapForNullInput() {
      // When
      Map<String, VenueWikidataEnrichment> result = client.queryEnrichmentData(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空集合应返回空 Map")
    void shouldReturnEmptyMapForEmptySet() {
      // When
      Map<String, VenueWikidataEnrichment> result = client.queryEnrichmentData(Set.of());

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("全部非法 ISSN-L 格式应返回空 Map（防止 SPARQL 注入）")
    void shouldReturnEmptyMapWhenAllIssnLsAreInvalid() {
      // Given - 全部非法格式
      Set<String> issnLs = Set.of("invalid", "\" . } #", "12345");

      // When
      Map<String, VenueWikidataEnrichment> result = client.queryEnrichmentData(issnLs);

      // Then - 过滤后无有效 ISSN-L，直接返回空 Map（不发送请求）
      assertThat(result).isEmpty();
    }
  }
}
