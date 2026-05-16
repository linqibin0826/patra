package dev.linqibin.patra.catalog.infra.adapter.integration.openalex;

import dev.linqibin.patra.catalog.domain.model.vo.venue.CitationMetrics;
import dev.linqibin.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import dev.linqibin.patra.catalog.domain.model.vo.venue.OpenAccessInfo.ApcPrice;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueOpenAlexEnrichment;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// OpenAlex Sources API 查询客户端。
///
/// 通过 OpenAlex REST API 批量查询期刊的引用指标、年度统计和开放获取信息。
/// 使用 ISSN（含 ISSN-L）作为匹配条件，返回 `CitationMetrics`、`VenuePublicationStats`
/// 和 `OpenAccessInfo`。
///
/// **API 调用模式**：
///
/// ```
/// GET /sources?filter=issn:{issn1}|{issn2}|...&per_page=200
///     &select=id,issn_l,works_count,cited_by_count,summary_stats,counts_by_year,
///             is_oa,is_in_doaj,apc_usd,apc_prices
/// ```
///
/// **子批次策略**：
///
/// - 每 50 个 ISSN-L 一个子批次（URL 长度约束）
/// - 子批次级 try-catch：单个子批次失败只 log.warn，不影响其他子批次
///
/// **注意**：此类非 Spring 组件，由 Boot 层 `OpenAlexConfiguration` 创建为 Bean。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class OpenAlexSourcesClient {

  /// ISSN 格式验证正则：4位数字-3位数字+1位校验位（数字或 X）。
  ///
  /// 防止注入：只有合法格式的 ISSN 才会被拼入查询参数。
  static final Pattern ISSN_PATTERN = Pattern.compile("^\\d{4}-\\d{3}[\\dXx]$");

  /// 子批次大小（每批最多 50 个 ISSN-L，控制 URL 长度）。
  static final int SUB_BATCH_SIZE = 50;

  /// select 参数：最小化响应体。
  private static final String SELECT_FIELDS =
      "id,issn_l,works_count,cited_by_count,summary_stats,counts_by_year,"
          + "is_oa,is_in_doaj,apc_usd,apc_prices";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  /// 批量查询 ISSN-L 对应的 OpenAlex 富化数据。
  ///
  /// @param issnLs ISSN-L 集合
  /// @return ISSN-L → 富化数据映射；查询失败时返回空 Map
  public Map<String, VenueOpenAlexEnrichment> queryEnrichmentData(Set<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Map.of();
    }

    // 过滤非法 ISSN 格式
    Set<String> validIssns =
        issnLs.stream()
            .filter(issn -> ISSN_PATTERN.matcher(issn).matches())
            .collect(Collectors.toSet());

    if (validIssns.isEmpty()) {
      log.debug("过滤后无有效 ISSN-L，跳过 OpenAlex 查询");
      return Map.of();
    }

    Map<String, VenueOpenAlexEnrichment> allResults = new HashMap<>();
    List<List<String>> subBatches = splitIntoSubBatches(validIssns);

    for (List<String> subBatch : subBatches) {
      try {
        String filter = buildIssnFilter(Set.copyOf(subBatch));
        log.debug("OpenAlex Sources 查询子批次：{} 个 ISSN-L", subBatch.size());

        String responseBody =
            restClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/sources")
                            .queryParam("filter", filter)
                            .queryParam("per_page", 200)
                            .queryParam("select", SELECT_FIELDS)
                            .build())
                .retrieve()
                .body(String.class);

        allResults.putAll(parseResponse(responseBody));

      } catch (Exception ex) {
        log.warn("OpenAlex Sources 子批次查询失败（{} 个 ISSN-L）", subBatch.size(), ex);
      }
    }

    log.debug("OpenAlex 富化查询完成：请求 {} 个 ISSN-L，匹配 {} 个", validIssns.size(), allResults.size());
    return allResults;
  }

  /// 构建 ISSN filter 参数。
  ///
  /// 格式：`issn:{issn1}|{issn2}|...`
  ///
  /// @param issns 已验证的 ISSN 集合
  /// @return filter 参数字符串
  String buildIssnFilter(Set<String> issns) {
    return "issn:" + String.join("|", issns);
  }

  /// 将 ISSN 集合拆分为子批次。
  ///
  /// @param issns ISSN 集合
  /// @return 子批次列表
  List<List<String>> splitIntoSubBatches(Set<String> issns) {
    List<String> issnList = new ArrayList<>(issns);
    List<List<String>> subBatches = new ArrayList<>();
    for (int i = 0; i < issnList.size(); i += SUB_BATCH_SIZE) {
      subBatches.add(issnList.subList(i, Math.min(i + SUB_BATCH_SIZE, issnList.size())));
    }
    return subBatches;
  }

  /// 从 OpenAlex ID URL 中提取短 ID。
  ///
  /// 例如 `https://openalex.org/S137773608` → `S137773608`
  ///
  /// @param openAlexId 完整 URL 或短 ID
  /// @return 短 ID
  static String extractShortId(String openAlexId) {
    if (openAlexId == null) {
      return null;
    }
    int lastSlash = openAlexId.lastIndexOf('/');
    return lastSlash >= 0 ? openAlexId.substring(lastSlash + 1) : openAlexId;
  }

  /// 解析 OpenAlex Sources API JSON 响应。
  ///
  /// 响应格式：
  ///
  /// ```json
  /// {
  ///   "results": [
  ///     {
  ///       "id": "https://openalex.org/S137773608",
  ///       "issn_l": "0028-0836",
  ///       "works_count": 150000,
  ///       "cited_by_count": 2500000,
  ///       "summary_stats": {
  ///         "h_index": 285,
  ///         "i10_index": 1200,
  ///         "2yr_mean_citedness": 3.45
  ///       },
  ///       "counts_by_year": [
  ///         {"year": 2024, "works_count": 1500, "cited_by_count": 25000, "oa_works_count": 800}
  ///       ]
  ///     }
  ///   ]
  /// }
  /// ```
  ///
  /// @param json 响应 JSON 字符串
  /// @return ISSN-L → 富化数据映射
  Map<String, VenueOpenAlexEnrichment> parseResponse(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }

    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode results = root.path("results");

      if (results.isMissingNode() || !results.isArray()) {
        return Map.of();
      }

      Map<String, VenueOpenAlexEnrichment> resultMap = new HashMap<>();

      for (JsonNode source : results) {
        try {
          String issnL = source.path("issn_l").asText("");
          if (issnL.isEmpty()) {
            continue;
          }

          String openAlexId = extractShortId(source.path("id").asText(""));
          CitationMetrics citationMetrics = parseCitationMetrics(source);
          List<VenuePublicationStats> yearlyStats = parseCountsByYear(source);
          OpenAccessInfo openAccessInfo = parseOpenAccessInfo(source);

          resultMap.put(
              issnL,
              VenueOpenAlexEnrichment.of(openAlexId, citationMetrics, yearlyStats, openAccessInfo));
        } catch (Exception e) {
          log.warn(
              "OpenAlex Source 解析失败（ISSN-L: {}）：{}",
              source.path("issn_l").asText("unknown"),
              e.getMessage());
        }
      }

      return resultMap;

    } catch (Exception e) {
      log.warn("OpenAlex 响应 JSON 解析失败", e);
      return Map.of();
    }
  }

  /// 从 Source JSON 节点中解析引用指标。
  ///
  /// @param source Source JSON 节点
  /// @return 引用指标值对象
  private CitationMetrics parseCitationMetrics(JsonNode source) {
    int worksCount = source.path("works_count").asInt(0);
    int citedByCount = source.path("cited_by_count").asInt(0);

    JsonNode summaryStats = source.path("summary_stats");
    if (summaryStats.isMissingNode()) {
      return CitationMetrics.ofBasic(worksCount, citedByCount);
    }

    Integer hIndex = summaryStats.has("h_index") ? summaryStats.path("h_index").asInt() : null;
    Integer i10Index =
        summaryStats.has("i10_index") ? summaryStats.path("i10_index").asInt() : null;
    BigDecimal twoYearMeanCitedness =
        summaryStats.has("2yr_mean_citedness")
            ? new BigDecimal(summaryStats.path("2yr_mean_citedness").asText())
            : null;

    return CitationMetrics.of(worksCount, citedByCount, hIndex, i10Index, twoYearMeanCitedness);
  }

  /// 从 Source JSON 节点中解析年度统计。
  ///
  /// @param source Source JSON 节点
  /// @return 年度统计列表
  private List<VenuePublicationStats> parseCountsByYear(JsonNode source) {
    JsonNode countsByYear = source.path("counts_by_year");
    if (countsByYear.isMissingNode() || !countsByYear.isArray()) {
      return List.of();
    }

    List<VenuePublicationStats> stats = new ArrayList<>();
    for (JsonNode yearNode : countsByYear) {
      try {
        int year = yearNode.path("year").asInt(0);
        int worksCount = yearNode.path("works_count").asInt(0);
        int citedByCount = yearNode.path("cited_by_count").asInt(0);
        Integer oaWorksCount =
            yearNode.has("oa_works_count") ? yearNode.path("oa_works_count").asInt() : null;

        stats.add(VenuePublicationStats.create(year, worksCount, citedByCount, oaWorksCount));
      } catch (Exception e) {
        log.debug("跳过无效的年度统计条目：{}", e.getMessage());
      }
    }
    return stats;
  }

  /// 从 Source JSON 节点中解析开放获取信息。
  ///
  /// 当 `is_oa`、`is_in_doaj`、`apc_usd`、`apc_prices` 全部缺失时返回 null，
  /// 表示该 Source 无 OA 相关数据。只要有任意一个字段存在就构建 `OpenAccessInfo`。
  ///
  /// **注意**：`oaType` 在 OpenAlex Source 级别不提供（OA 类型是 work 级别概念），
  /// 固定传入 null。
  ///
  /// @param source Source JSON 节点
  /// @return 开放获取信息，全部缺失时返回 null
  private OpenAccessInfo parseOpenAccessInfo(JsonNode source) {
    boolean hasIsOa = source.has("is_oa");
    boolean hasIsInDoaj = source.has("is_in_doaj");
    boolean hasApcUsd = source.has("apc_usd");
    boolean hasApcPrices = source.has("apc_prices");

    if (!hasIsOa && !hasIsInDoaj && !hasApcUsd && !hasApcPrices) {
      return null;
    }

    boolean isOa = source.path("is_oa").asBoolean(false);
    boolean isInDoaj = source.path("is_in_doaj").asBoolean(false);
    Integer apcUsd =
        hasApcUsd && !source.path("apc_usd").isNull() ? source.path("apc_usd").asInt() : null;

    List<ApcPrice> apcPrices = parseApcPrices(source.path("apc_prices"));

    return OpenAccessInfo.of(isOa, isInDoaj, null, apcUsd, apcPrices);
  }

  /// 从 JSON 节点中解析 APC 价格列表。
  ///
  /// @param apcPricesNode apc_prices JSON 节点
  /// @return APC 价格列表，缺失或 null 时返回空列表
  private List<ApcPrice> parseApcPrices(JsonNode apcPricesNode) {
    if (apcPricesNode.isMissingNode() || apcPricesNode.isNull() || !apcPricesNode.isArray()) {
      return List.of();
    }

    List<ApcPrice> prices = new ArrayList<>();
    for (JsonNode priceNode : apcPricesNode) {
      int price = priceNode.path("price").asInt(0);
      String currency = priceNode.path("currency").asText("");
      if (!currency.isEmpty()) {
        prices.add(ApcPrice.of(price, currency));
      }
    }
    return prices;
  }
}
