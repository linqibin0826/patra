package dev.linqibin.patra.catalog.infra.adapter.integration.scopus;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusVenueData;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// ScopusApiClient 单元测试。
///
/// **测试策略**：
///
/// - JSON 响应解析：验证 `parseResponse()` 正确映射到 `ScopusVenueData`
/// - 边界条件：空响应、期刊未找到、畸形 JSON
/// - Quartile 推导：从 percentile 推导 Q1-Q4
/// - 历史 CiteScore 趋势解析
///
/// **测试 fixture 基于 Scopus Serial Title API 真实响应**（Nature, ISSN 0028-0836）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ScopusApiClient 单元测试")
class ScopusApiClientTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private ScopusApiClient client;

  @BeforeEach
  void setUp() {
    // RestClient 仅在 HTTP 调用中使用，本测试类聚焦解析逻辑，传入 null 即可
    client = new ScopusApiClient(null, objectMapper);
  }

  @Nested
  @DisplayName("JSON 响应解析 - 正常场景")
  class ParseResponseNormalTest {

    @Test
    @DisplayName("应该正确解析包含完整数据的响应（基于 Nature 真实数据）")
    void shouldParseCompleteResponse() {
      // Given — 基于 Scopus API 真实响应简化
      String json =
          """
          {
            "serial-metadata-response": {
              "entry": [{
                "dc:title": "Nature",
                "source-id": "21206",
                "prism:issn": "0028-0836",
                "prism:eIssn": "1476-4687",
                "subject-area": [
                  {"@code": "1000", "@abbrev": "MULT", "$": "Multidisciplinary"}
                ],
                "SNIPList": {"SNIP": [{"@year": "2024", "$": "10.161"}]},
                "SJRList": {"SJR": [{"@year": "2024", "$": "18.288"}]},
                "citeScoreYearInfoList": {
                  "citeScoreCurrentMetric": "78.1",
                  "citeScoreCurrentMetricYear": "2024",
                  "citeScoreTracker": "77.2",
                  "citeScoreTrackerYear": "2025",
                  "citeScoreYearInfo": [
                    {
                      "@year": "2024",
                      "@status": "Complete",
                      "citeScoreInformationList": [{"citeScoreInfo": [{
                        "docType": "all",
                        "scholarlyOutput": "4992",
                        "citationCount": "390062",
                        "citeScore": "78.1",
                        "percentCited": "94",
                        "citeScoreSubjectRank": [
                          {"subjectCode": "1000", "rank": "1", "percentile": "99"}
                        ]
                      }]}]
                    },
                    {
                      "@year": "2023",
                      "@status": "Complete",
                      "citeScoreInformationList": [{"citeScoreInfo": [{
                        "docType": "all",
                        "scholarlyOutput": "4895",
                        "citationCount": "440674",
                        "citeScore": "90.0",
                        "percentCited": "94",
                        "citeScoreSubjectRank": [
                          {"subjectCode": "1000", "rank": "1", "percentile": "99"}
                        ]
                      }]}]
                    }
                  ]
                }
              }]
            }
          }
          """;

      // When
      Optional<ScopusVenueData> result = client.parseResponse(json);

      // Then
      assertThat(result).isPresent();
      ScopusVenueData data = result.get();

      // 基本信息
      assertThat(data.scopusSourceId()).isEqualTo("21206");
      assertThat(data.title()).isEqualTo("Nature");

      // 当年指标
      assertThat(data.citeScore()).isEqualTo(78.1);
      assertThat(data.citeScoreTracker()).isEqualTo(77.2);
      assertThat(data.sjr()).isEqualTo(18.288);
      assertThat(data.snip()).isEqualTo(10.161);

      // 学科分区（percentile 99 → Q1）
      assertThat(data.subjectArea()).isEqualTo("Multidisciplinary");
      assertThat(data.quartile()).isEqualTo("Q1");
      assertThat(data.percentile()).isEqualTo(99.0);

      // 当年发文/引用（来自最新 Complete 年份的 citeScoreInfo）
      assertThat(data.documentCount()).isEqualTo(4992);
      assertThat(data.citationCount()).isEqualTo(390062);
      assertThat(data.percentCited()).isEqualTo(94.0);

      // 历史趋势
      assertThat(data.yearlyMetrics()).hasSize(2);
      assertThat(data.yearlyMetrics().get(0).year()).isEqualTo(2024);
      assertThat(data.yearlyMetrics().get(0).citeScore()).isEqualTo(78.1);
      assertThat(data.yearlyMetrics().get(1).year()).isEqualTo(2023);
      assertThat(data.yearlyMetrics().get(1).citeScore()).isEqualTo(90.0);
      assertThat(data.yearlyMetrics().get(1).documentCount()).isEqualTo(4895);
      assertThat(data.yearlyMetrics().get(1).citationCount()).isEqualTo(440674);
    }

    @Test
    @DisplayName("SJR 和 SNIP 只有最新年数据时应正确解析")
    void shouldParseSjrAndSnipFromLatestYear() {
      // Given
      String json =
          """
          {
            "serial-metadata-response": {
              "entry": [{
                "dc:title": "Test Journal",
                "source-id": "12345",
                "prism:issn": "1234-5678",
                "subject-area": [{"@code": "2700", "@abbrev": "MED", "$": "Medicine"}],
                "SNIPList": {"SNIP": [{"@year": "2024", "$": "3.456"}]},
                "SJRList": {"SJR": [{"@year": "2024", "$": "2.789"}]},
                "citeScoreYearInfoList": {
                  "citeScoreCurrentMetric": "12.5",
                  "citeScoreCurrentMetricYear": "2024",
                  "citeScoreTracker": "13.0",
                  "citeScoreTrackerYear": "2025",
                  "citeScoreYearInfo": []
                }
              }]
            }
          }
          """;

      // When
      Optional<ScopusVenueData> result = client.parseResponse(json);

      // Then
      assertThat(result).isPresent();
      ScopusVenueData data = result.get();
      assertThat(data.sjr()).isEqualTo(2.789);
      assertThat(data.snip()).isEqualTo(3.456);
      assertThat(data.citeScore()).isEqualTo(12.5);
      assertThat(data.citeScoreTracker()).isEqualTo(13.0);
      assertThat(data.yearlyMetrics()).isEmpty();
    }

    @Test
    @DisplayName("多学科排名时应取第一个学科的 percentile")
    void shouldUseFirstSubjectRankPercentile() {
      // Given — 2015 年 Nature 有两个学科排名（2700 和 1000）
      String json =
          """
          {
            "serial-metadata-response": {
              "entry": [{
                "dc:title": "Nature",
                "source-id": "21206",
                "prism:issn": "0028-0836",
                "subject-area": [{"@code": "1000", "$": "Multidisciplinary"}],
                "SNIPList": {"SNIP": [{"@year": "2024", "$": "10.0"}]},
                "SJRList": {"SJR": [{"@year": "2024", "$": "18.0"}]},
                "citeScoreYearInfoList": {
                  "citeScoreCurrentMetric": "78.1",
                  "citeScoreCurrentMetricYear": "2024",
                  "citeScoreYearInfo": [
                    {
                      "@year": "2015",
                      "@status": "Complete",
                      "citeScoreInformationList": [{"citeScoreInfo": [{
                        "scholarlyOutput": "4735",
                        "citationCount": "244491",
                        "citeScore": "51.6",
                        "percentCited": "86",
                        "citeScoreSubjectRank": [
                          {"subjectCode": "2700", "rank": "14", "percentile": "99"},
                          {"subjectCode": "1000", "rank": "1", "percentile": "99"}
                        ]
                      }]}]
                    }
                  ]
                }
              }]
            }
          }
          """;

      // When
      Optional<ScopusVenueData> result = client.parseResponse(json);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().yearlyMetrics()).hasSize(1);
      assertThat(result.get().yearlyMetrics().getFirst().citeScore()).isEqualTo(51.6);
    }
  }

  @Nested
  @DisplayName("Quartile 推导测试")
  class QuartileDerivationTest {

    @Test
    @DisplayName("percentile >= 75 应推导为 Q1")
    void shouldDeriveQ1ForHighPercentile() {
      assertThat(ScopusApiClient.deriveQuartile(99.0)).isEqualTo("Q1");
      assertThat(ScopusApiClient.deriveQuartile(75.0)).isEqualTo("Q1");
    }

    @Test
    @DisplayName("50 <= percentile < 75 应推导为 Q2")
    void shouldDeriveQ2ForMediumHighPercentile() {
      assertThat(ScopusApiClient.deriveQuartile(74.0)).isEqualTo("Q2");
      assertThat(ScopusApiClient.deriveQuartile(50.0)).isEqualTo("Q2");
    }

    @Test
    @DisplayName("25 <= percentile < 50 应推导为 Q3")
    void shouldDeriveQ3ForMediumLowPercentile() {
      assertThat(ScopusApiClient.deriveQuartile(49.0)).isEqualTo("Q3");
      assertThat(ScopusApiClient.deriveQuartile(25.0)).isEqualTo("Q3");
    }

    @Test
    @DisplayName("percentile < 25 应推导为 Q4")
    void shouldDeriveQ4ForLowPercentile() {
      assertThat(ScopusApiClient.deriveQuartile(24.0)).isEqualTo("Q4");
      assertThat(ScopusApiClient.deriveQuartile(1.0)).isEqualTo("Q4");
    }

    @Test
    @DisplayName("null percentile 应返回 null")
    void shouldReturnNullForNullPercentile() {
      assertThat(ScopusApiClient.deriveQuartile(null)).isNull();
    }
  }

  @Nested
  @DisplayName("JSON 响应解析 - 边界条件")
  class ParseResponseBoundaryTest {

    @Test
    @DisplayName("null 响应体应返回 empty")
    void shouldReturnEmptyForNullResponse() {
      assertThat(client.parseResponse(null)).isEmpty();
    }

    @Test
    @DisplayName("空白响应体应返回 empty")
    void shouldReturnEmptyForBlankResponse() {
      assertThat(client.parseResponse("   ")).isEmpty();
    }

    @Test
    @DisplayName("畸形 JSON 应返回 empty")
    void shouldReturnEmptyForMalformedJson() {
      assertThat(client.parseResponse("not json")).isEmpty();
    }

    @Test
    @DisplayName("空 entry 数组应返回 empty")
    void shouldReturnEmptyForEmptyEntry() {
      String json =
          """
          {"serial-metadata-response": {"entry": []}}
          """;
      assertThat(client.parseResponse(json)).isEmpty();
    }

    @Test
    @DisplayName("缺少 serial-metadata-response 节点应返回 empty")
    void shouldReturnEmptyForMissingRoot() {
      String json =
          """
          {"error": "not found"}
          """;
      assertThat(client.parseResponse(json)).isEmpty();
    }

    @Test
    @DisplayName("缺少 citeScoreYearInfoList 时应仍返回基本信息")
    void shouldReturnBasicInfoWhenCiteScoreInfoMissing() {
      // Given
      String json =
          """
          {
            "serial-metadata-response": {
              "entry": [{
                "dc:title": "Test Journal",
                "source-id": "99999",
                "prism:issn": "9999-9999",
                "subject-area": [{"$": "Medicine"}],
                "SNIPList": {"SNIP": [{"@year": "2024", "$": "1.5"}]},
                "SJRList": {"SJR": [{"@year": "2024", "$": "0.8"}]}
              }]
            }
          }
          """;

      // When
      Optional<ScopusVenueData> result = client.parseResponse(json);

      // Then
      assertThat(result).isPresent();
      ScopusVenueData data = result.get();
      assertThat(data.scopusSourceId()).isEqualTo("99999");
      assertThat(data.title()).isEqualTo("Test Journal");
      assertThat(data.sjr()).isEqualTo(0.8);
      assertThat(data.snip()).isEqualTo(1.5);
      assertThat(data.citeScore()).isNull();
      assertThat(data.yearlyMetrics()).isEmpty();
    }

    @Test
    @DisplayName("SNIPList/SJRList 缺失时对应字段应为 null")
    void shouldReturnNullWhenSnipSjrMissing() {
      // Given
      String json =
          """
          {
            "serial-metadata-response": {
              "entry": [{
                "dc:title": "Minimal Journal",
                "source-id": "11111",
                "prism:issn": "1111-1111",
                "subject-area": [],
                "citeScoreYearInfoList": {
                  "citeScoreCurrentMetric": "5.0",
                  "citeScoreCurrentMetricYear": "2024",
                  "citeScoreYearInfo": []
                }
              }]
            }
          }
          """;

      // When
      Optional<ScopusVenueData> result = client.parseResponse(json);

      // Then
      assertThat(result).isPresent();
      ScopusVenueData data = result.get();
      assertThat(data.sjr()).isNull();
      assertThat(data.snip()).isNull();
      assertThat(data.citeScore()).isEqualTo(5.0);
      assertThat(data.subjectArea()).isNull();
    }
  }

  @Nested
  @DisplayName("ISSN 校验测试")
  class IssnValidationTest {

    @Test
    @DisplayName("合法 ISSN 应通过校验")
    void shouldAcceptValidIssn() {
      assertThat(ScopusApiClient.isValidIssn("0028-0836")).isTrue();
      assertThat(ScopusApiClient.isValidIssn("1234-567X")).isTrue();
    }

    @Test
    @DisplayName("非法 ISSN 应被拒绝")
    void shouldRejectInvalidIssn() {
      assertThat(ScopusApiClient.isValidIssn(null)).isFalse();
      assertThat(ScopusApiClient.isValidIssn("")).isFalse();
      assertThat(ScopusApiClient.isValidIssn("invalid")).isFalse();
      assertThat(ScopusApiClient.isValidIssn("12345678")).isFalse();
    }
  }
}
