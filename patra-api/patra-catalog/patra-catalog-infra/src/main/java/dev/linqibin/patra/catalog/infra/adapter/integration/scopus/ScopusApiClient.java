package dev.linqibin.patra.catalog.infra.adapter.integration.scopus;

import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusVenueData;
import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusVenueData.YearlyMetric;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// Scopus Serial Title API 查询客户端。
///
/// 通过 Elsevier Scopus REST API 查询期刊的 CiteScore、SJR、SNIP 等指标数据。
/// 使用 ISSN 作为查询条件，返回 {@link ScopusVenueData}。
///
/// **API 调用模式**：
///
/// ```
/// GET /content/serial/title?issn={issn}&view=CITESCORE
/// Header: X-ELS-APIKey: {apiKey}
/// ```
///
/// **响应结构**：
///
/// ```
/// serial-metadata-response.entry[0]
///   ├── dc:title, source-id, prism:issn
///   ├── SJRList.SJR[0].$ (最新年)
///   ├── SNIPList.SNIP[0].$ (最新年)
///   ├── subject-area[0].$ (主学科)
///   └── citeScoreYearInfoList
///         ├── citeScoreCurrentMetric (当年 CiteScore)
///         ├── citeScoreTracker (当年预估)
///         └── citeScoreYearInfo[] (逐年历史数据)
/// ```
///
/// **注意**：此类非 Spring 组件，由 Boot 层 `ScopusConfiguration` 创建为 Bean。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class ScopusApiClient {

  /// ISSN 格式验证正则：4位数字-3位数字+1位校验位（数字或 X）。
  private static final Pattern ISSN_PATTERN = Pattern.compile("^\\d{4}-\\d{3}[\\dXx]$");

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  /// 通过 ISSN 查询 Scopus 期刊数据。
  ///
  /// @param issn ISSN 标识符
  /// @return 期刊指标数据，未找到或失败时返回 `Optional.empty()`
  public Optional<ScopusVenueData> findByIssn(String issn) {
    if (!isValidIssn(issn)) {
      return Optional.empty();
    }

    try {
      String body =
          restClient
              .get()
              .uri("/content/serial/title?issn={issn}&view=CITESCORE", issn)
              .retrieve()
              .body(String.class);
      return parseResponse(body);
    } catch (Exception e) {
      log.warn("Scopus API 调用失败 [issn={}]: {}", issn, e.getMessage());
      return Optional.empty();
    }
  }

  /// 解析 Scopus Serial Title API JSON 响应。
  ///
  /// @param json 原始 JSON 字符串
  /// @return 解析后的期刊数据
  Optional<ScopusVenueData> parseResponse(String json) {
    if (json == null || json.isBlank()) {
      return Optional.empty();
    }

    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode entries = root.path("serial-metadata-response").path("entry");

      if (entries.isMissingNode() || !entries.isArray() || entries.isEmpty()) {
        return Optional.empty();
      }

      JsonNode entry = entries.get(0);
      return Optional.of(parseEntry(entry));
    } catch (Exception e) {
      log.warn("Scopus 响应解析失败: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /// 解析单个 entry 节点为 ScopusVenueData。
  private ScopusVenueData parseEntry(JsonNode entry) {
    String sourceId = textOrNull(entry, "source-id");
    String title = textOrNull(entry, "dc:title");

    // SJR / SNIP（仅最新年）
    Double sjr = parseMetricValue(entry, "SJRList", "SJR");
    Double snip = parseMetricValue(entry, "SNIPList", "SNIP");

    // 主学科领域
    String subjectArea = parseSubjectArea(entry);

    // CiteScore 指标
    JsonNode citeScoreList = entry.path("citeScoreYearInfoList");
    Double citeScore = parseDoubleOrNull(citeScoreList, "citeScoreCurrentMetric");
    Double citeScoreTracker = parseDoubleOrNull(citeScoreList, "citeScoreTracker");

    // 逐年历史数据
    List<YearlyMetric> yearlyMetrics = parseYearlyMetrics(citeScoreList);

    // 当年详细指标（来自最新 Complete 年份的 citeScoreInfo）
    Integer documentCount = null;
    Integer citationCount = null;
    Double percentCited = null;
    Double percentile = null;

    if (!yearlyMetrics.isEmpty()) {
      YearlyMetric latest = yearlyMetrics.getFirst();
      documentCount = latest.documentCount();
      citationCount = latest.citationCount();
      percentCited = latest.percentCited();
    }

    // percentile 从最新年的 citeScoreSubjectRank 取
    percentile = findLatestPercentile(citeScoreList);
    String quartile = deriveQuartile(percentile);

    return ScopusVenueData.builder()
        .scopusSourceId(sourceId)
        .title(title)
        .citeScore(citeScore)
        .citeScoreTracker(citeScoreTracker)
        .sjr(sjr)
        .snip(snip)
        .documentCount(documentCount)
        .citationCount(citationCount)
        .percentCited(percentCited)
        .subjectArea(subjectArea)
        .quartile(quartile)
        .percentile(percentile)
        .yearlyMetrics(yearlyMetrics)
        .build();
  }

  /// 解析 SJRList/SNIPList 中的指标值。
  ///
  /// 结构：`{listName}.{metricName}[0].$`
  private Double parseMetricValue(JsonNode entry, String listName, String metricName) {
    JsonNode list = entry.path(listName).path(metricName);
    if (list.isMissingNode() || !list.isArray() || list.isEmpty()) {
      return null;
    }
    return parseDouble(list.get(0).path("$").asText(null));
  }

  /// 解析主学科领域：`subject-area[0].$`
  private String parseSubjectArea(JsonNode entry) {
    JsonNode areas = entry.path("subject-area");
    if (areas.isMissingNode() || !areas.isArray() || areas.isEmpty()) {
      return null;
    }
    String area = areas.get(0).path("$").asText(null);
    return (area != null && !area.isBlank()) ? area : null;
  }

  /// 解析逐年 CiteScore 历史数据。
  private List<YearlyMetric> parseYearlyMetrics(JsonNode citeScoreList) {
    JsonNode yearInfos = citeScoreList.path("citeScoreYearInfo");
    if (yearInfos.isMissingNode() || !yearInfos.isArray()) {
      return List.of();
    }

    List<YearlyMetric> metrics = new ArrayList<>();
    for (JsonNode yearInfo : yearInfos) {
      String status = yearInfo.path("@status").asText("");
      // 跳过 In-Progress 年份，只保留 Complete
      if (!"Complete".equals(status)) {
        continue;
      }

      try {
        int year = Integer.parseInt(yearInfo.path("@year").asText("0"));
        JsonNode citeScoreInfo =
            yearInfo.path("citeScoreInformationList").path(0).path("citeScoreInfo").path(0);

        if (citeScoreInfo.isMissingNode()) {
          continue;
        }

        metrics.add(
            YearlyMetric.builder()
                .year(year)
                .citeScore(parseDouble(citeScoreInfo.path("citeScore").asText(null)))
                .documentCount(parseInteger(citeScoreInfo.path("scholarlyOutput").asText(null)))
                .citationCount(parseInteger(citeScoreInfo.path("citationCount").asText(null)))
                .percentCited(parseDouble(citeScoreInfo.path("percentCited").asText(null)))
                .build());
      } catch (NumberFormatException e) {
        log.debug("跳过无效年份数据: {}", e.getMessage());
      }
    }
    return metrics;
  }

  /// 从最新年的 citeScoreSubjectRank 取 percentile。
  private Double findLatestPercentile(JsonNode citeScoreList) {
    JsonNode yearInfos = citeScoreList.path("citeScoreYearInfo");
    if (yearInfos.isMissingNode() || !yearInfos.isArray()) {
      return null;
    }

    for (JsonNode yearInfo : yearInfos) {
      if (!"Complete".equals(yearInfo.path("@status").asText(""))) {
        continue;
      }

      JsonNode citeScoreInfo =
          yearInfo.path("citeScoreInformationList").path(0).path("citeScoreInfo").path(0);
      JsonNode ranks = citeScoreInfo.path("citeScoreSubjectRank");

      if (!ranks.isMissingNode() && ranks.isArray() && !ranks.isEmpty()) {
        return parseDouble(ranks.get(0).path("percentile").asText(null));
      }

      // 只看第一个 Complete 年份
      break;
    }
    return null;
  }

  /// 从 percentile 推导 CiteScore quartile。
  ///
  /// - percentile >= 75 → Q1
  /// - percentile >= 50 → Q2
  /// - percentile >= 25 → Q3
  /// - percentile < 25 → Q4
  ///
  /// @param percentile 百分位排名（可为 null）
  /// @return Q1-Q4，null 时返回 null
  static String deriveQuartile(Double percentile) {
    if (percentile == null) {
      return null;
    }
    if (percentile >= 75) {
      return "Q1";
    }
    if (percentile >= 50) {
      return "Q2";
    }
    if (percentile >= 25) {
      return "Q3";
    }
    return "Q4";
  }

  /// 校验 ISSN 格式。
  ///
  /// @param issn ISSN 字符串
  /// @return true 如果格式合法
  static boolean isValidIssn(String issn) {
    return issn != null && ISSN_PATTERN.matcher(issn).matches();
  }

  /// 安全提取文本值。
  private String textOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    String text = value.asText(null);
    return (text != null && !text.isBlank()) ? text : null;
  }

  /// 安全解析 JsonNode 中的 Double 字段。
  private Double parseDoubleOrNull(JsonNode node, String field) {
    return parseDouble(node.path(field).asText(null));
  }

  /// 安全解析 Double。
  private Double parseDouble(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /// 安全解析 Integer。
  private Integer parseInteger(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
