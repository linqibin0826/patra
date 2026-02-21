package com.patra.catalog.infra.adapter.integration.openalex;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.vo.venue.VenueOpenAlexEnrichment;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// OpenAlexSourcesClient 单元测试。
///
/// **测试策略**：
///
/// - ISSN 过滤构建：验证 buildIssnFilter() 正确生成 filter 参数
/// - OpenAlex ID 提取：验证 extractShortId() 从完整 URL 中提取短 ID
/// - JSON 响应解析：验证 parseResponse() 正确映射到领域模型
/// - 边界条件：空输入、空响应、畸形响应
/// - 子批次拆分逻辑
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OpenAlexSourcesClient 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class OpenAlexSourcesClientTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private OpenAlexSourcesClient client;

  @BeforeEach
  void setUp() {
    // RestClient 仅在 HTTP 调用中使用，本测试类聚焦解析和构建逻辑，传入 null 即可
    client = new OpenAlexSourcesClient(null, objectMapper);
  }

  @Nested
  @DisplayName("ISSN 过滤条件构建测试")
  class IssnFilterBuildTest {

    @Test
    @DisplayName("应该正确构建包含多个 ISSN 的 filter 参数")
    void shouldBuildFilterWithMultipleIssns() {
      // Given
      Set<String> issns = Set.of("0028-0836", "0140-6736");

      // When
      String filter = client.buildIssnFilter(issns);

      // Then — 格式为 issn:{issn1}|{issn2}
      assertThat(filter).startsWith("issn:");
      assertThat(filter).contains("0028-0836");
      assertThat(filter).contains("0140-6736");
      assertThat(filter).contains("|");
    }

    @Test
    @DisplayName("单个 ISSN 应该不包含分隔符")
    void shouldBuildFilterWithSingleIssn() {
      // Given
      Set<String> issns = Set.of("0028-0836");

      // When
      String filter = client.buildIssnFilter(issns);

      // Then
      assertThat(filter).isEqualTo("issn:0028-0836");
    }
  }

  @Nested
  @DisplayName("OpenAlex ID 提取测试")
  class ExtractShortIdTest {

    @Test
    @DisplayName("应该从完整 URL 中提取短 ID")
    void shouldExtractShortIdFromUrl() {
      // When
      String shortId = OpenAlexSourcesClient.extractShortId("https://openalex.org/S137773608");

      // Then
      assertThat(shortId).isEqualTo("S137773608");
    }

    @Test
    @DisplayName("已经是短 ID 时应原样返回")
    void shouldReturnShortIdAsIs() {
      // When
      String shortId = OpenAlexSourcesClient.extractShortId("S137773608");

      // Then
      assertThat(shortId).isEqualTo("S137773608");
    }

    @Test
    @DisplayName("null 输入应返回 null")
    void shouldReturnNullForNullInput() {
      assertThat(OpenAlexSourcesClient.extractShortId(null)).isNull();
    }

    @Test
    @DisplayName("空字符串应返回空字符串")
    void shouldReturnEmptyForEmptyInput() {
      assertThat(OpenAlexSourcesClient.extractShortId("")).isEmpty();
    }
  }

  @Nested
  @DisplayName("JSON 响应解析测试 - 正常场景")
  class ParseResponseNormalTest {

    @Test
    @DisplayName("应该正确解析包含完整数据的响应")
    void shouldParseCompleteResponse() {
      // Given
      String json =
          """
          {
            "results": [
              {
                "id": "https://openalex.org/S137773608",
                "issn_l": "0028-0836",
                "works_count": 150000,
                "cited_by_count": 2500000,
                "summary_stats": {
                  "h_index": 285,
                  "i10_index": 1200,
                  "2yr_mean_citedness": 3.45
                },
                "counts_by_year": [
                  {"year": 2024, "works_count": 1500, "cited_by_count": 25000, "oa_works_count": 800},
                  {"year": 2023, "works_count": 1400, "cited_by_count": 22000, "oa_works_count": 700}
                ]
              }
            ]
          }
          """;

      // When
      Map<String, VenueOpenAlexEnrichment> result = client.parseResponse(json);

      // Then
      assertThat(result).hasSize(1);
      VenueOpenAlexEnrichment enrichment = result.get("0028-0836");
      assertThat(enrichment).isNotNull();
      assertThat(enrichment.openAlexId()).isEqualTo("S137773608");

      // 验证引用指标
      assertThat(enrichment.citationMetrics().worksCount()).isEqualTo(150000);
      assertThat(enrichment.citationMetrics().citedByCount()).isEqualTo(2500000);
      assertThat(enrichment.citationMetrics().hIndex()).isEqualTo(285);
      assertThat(enrichment.citationMetrics().i10Index()).isEqualTo(1200);
      assertThat(enrichment.citationMetrics().twoYearMeanCitedness()).isEqualByComparingTo("3.45");

      // 验证年度统计
      assertThat(enrichment.yearlyStats()).hasSize(2);
      assertThat(enrichment.yearlyStats().getFirst().year()).isEqualTo(2024);
      assertThat(enrichment.yearlyStats().getFirst().worksCount()).isEqualTo(1500);
      assertThat(enrichment.yearlyStats().getFirst().citedByCount()).isEqualTo(25000);
      assertThat(enrichment.yearlyStats().getFirst().oaWorksCount()).isEqualTo(800);
    }

    @Test
    @DisplayName("应该正确解析多个 Source 的响应")
    void shouldParseMultipleSources() {
      // Given
      String json =
          """
          {
            "results": [
              {
                "id": "https://openalex.org/S137773608",
                "issn_l": "0028-0836",
                "works_count": 150000,
                "cited_by_count": 2500000,
                "summary_stats": {"h_index": 285, "i10_index": 1200, "2yr_mean_citedness": 3.45},
                "counts_by_year": []
              },
              {
                "id": "https://openalex.org/S49861241",
                "issn_l": "0140-6736",
                "works_count": 300000,
                "cited_by_count": 5000000,
                "summary_stats": {"h_index": 400, "i10_index": 2000, "2yr_mean_citedness": 5.67},
                "counts_by_year": []
              }
            ]
          }
          """;

      // When
      Map<String, VenueOpenAlexEnrichment> result = client.parseResponse(json);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result.get("0028-0836").openAlexId()).isEqualTo("S137773608");
      assertThat(result.get("0140-6736").openAlexId()).isEqualTo("S49861241");
      assertThat(result.get("0140-6736").citationMetrics().hIndex()).isEqualTo(400);
    }

    @Test
    @DisplayName("summary_stats 缺失时应创建基础引用指标")
    void shouldCreateBasicMetricsWhenSummaryStatsMissing() {
      // Given
      String json =
          """
          {
            "results": [
              {
                "id": "https://openalex.org/S137773608",
                "issn_l": "0028-0836",
                "works_count": 150000,
                "cited_by_count": 2500000,
                "counts_by_year": []
              }
            ]
          }
          """;

      // When
      Map<String, VenueOpenAlexEnrichment> result = client.parseResponse(json);

      // Then
      VenueOpenAlexEnrichment enrichment = result.get("0028-0836");
      assertThat(enrichment.citationMetrics().worksCount()).isEqualTo(150000);
      assertThat(enrichment.citationMetrics().citedByCount()).isEqualTo(2500000);
      assertThat(enrichment.citationMetrics().hIndex()).isNull();
      assertThat(enrichment.citationMetrics().i10Index()).isNull();
      assertThat(enrichment.citationMetrics().twoYearMeanCitedness()).isNull();
    }

    @Test
    @DisplayName("counts_by_year 缺失时应返回空列表")
    void shouldReturnEmptyStatsWhenCountsByYearMissing() {
      // Given
      String json =
          """
          {
            "results": [
              {
                "id": "https://openalex.org/S137773608",
                "issn_l": "0028-0836",
                "works_count": 150000,
                "cited_by_count": 2500000,
                "summary_stats": {"h_index": 285, "i10_index": 1200, "2yr_mean_citedness": 3.45}
              }
            ]
          }
          """;

      // When
      Map<String, VenueOpenAlexEnrichment> result = client.parseResponse(json);

      // Then
      assertThat(result.get("0028-0836").yearlyStats()).isEmpty();
    }

    @Test
    @DisplayName("oa_works_count 缺失时应为 null")
    void shouldHandleMissingOaWorksCount() {
      // Given
      String json =
          """
          {
            "results": [
              {
                "id": "https://openalex.org/S137773608",
                "issn_l": "0028-0836",
                "works_count": 150000,
                "cited_by_count": 2500000,
                "summary_stats": {"h_index": 285, "i10_index": 1200, "2yr_mean_citedness": 3.45},
                "counts_by_year": [
                  {"year": 2024, "works_count": 1500, "cited_by_count": 25000}
                ]
              }
            ]
          }
          """;

      // When
      Map<String, VenueOpenAlexEnrichment> result = client.parseResponse(json);

      // Then
      assertThat(result.get("0028-0836").yearlyStats().getFirst().oaWorksCount()).isNull();
    }
  }

  @Nested
  @DisplayName("JSON 响应解析测试 - 边界条件")
  class ParseResponseBoundaryTest {

    @Test
    @DisplayName("空 results 数组应返回空 Map")
    void shouldReturnEmptyMapForEmptyResults() {
      // Given
      String json =
          """
          {"results": []}
          """;

      // When
      Map<String, VenueOpenAlexEnrichment> result = client.parseResponse(json);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 响应体应返回空 Map")
    void shouldReturnEmptyMapForNullResponse() {
      assertThat(client.parseResponse(null)).isEmpty();
    }

    @Test
    @DisplayName("空白响应体应返回空 Map")
    void shouldReturnEmptyMapForBlankResponse() {
      assertThat(client.parseResponse("   ")).isEmpty();
    }

    @Test
    @DisplayName("畸形 JSON 应返回空 Map")
    void shouldReturnEmptyMapForMalformedJson() {
      assertThat(client.parseResponse("not json")).isEmpty();
    }

    @Test
    @DisplayName("缺少 results 节点应返回空 Map")
    void shouldReturnEmptyMapForMissingResults() {
      String json =
          """
          {"meta": {"count": 0}}
          """;
      assertThat(client.parseResponse(json)).isEmpty();
    }

    @Test
    @DisplayName("缺少 issn_l 的 Source 应被跳过")
    void shouldSkipSourceWithoutIssnL() {
      // Given
      String json =
          """
          {
            "results": [
              {
                "id": "https://openalex.org/S137773608",
                "works_count": 150000,
                "cited_by_count": 2500000,
                "counts_by_year": []
              }
            ]
          }
          """;

      // When
      Map<String, VenueOpenAlexEnrichment> result = client.parseResponse(json);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("空输入测试")
  class EmptyInputTest {

    @Test
    @DisplayName("null 输入应返回空 Map")
    void shouldReturnEmptyMapForNullInput() {
      assertThat(client.queryEnrichmentData(null)).isEmpty();
    }

    @Test
    @DisplayName("空集合应返回空 Map")
    void shouldReturnEmptyMapForEmptySet() {
      assertThat(client.queryEnrichmentData(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("全部非法 ISSN 格式应返回空 Map")
    void shouldReturnEmptyMapWhenAllIssnsAreInvalid() {
      // Given
      Set<String> issns = Set.of("invalid", "12345", "abcd-efgh");

      // When
      Map<String, VenueOpenAlexEnrichment> result = client.queryEnrichmentData(issns);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("子批次拆分测试")
  class SubBatchSplitTest {

    @Test
    @DisplayName("应该按 SUB_BATCH_SIZE 拆分 ISSN 集合")
    void shouldSplitIssnsBySubBatchSize() {
      // Given — 构建超过子批次大小的 ISSN 集合
      var issns = new java.util.HashSet<String>();
      for (int i = 0; i < 60; i++) {
        issns.add(String.format("%04d-%04d", i / 10, i));
      }

      // When
      var subBatches = client.splitIntoSubBatches(issns);

      // Then — 60 个 ISSN，每批 50 个，应分 2 批
      assertThat(subBatches).hasSize(2);
      assertThat(subBatches.getFirst()).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("不超过子批次大小时应只有一个批次")
    void shouldReturnSingleBatchWhenWithinLimit() {
      // Given
      Set<String> issns = Set.of("0028-0836", "0140-6736");

      // When
      var subBatches = client.splitIntoSubBatches(issns);

      // Then
      assertThat(subBatches).hasSize(1);
      assertThat(subBatches.getFirst()).hasSize(2);
    }
  }
}
