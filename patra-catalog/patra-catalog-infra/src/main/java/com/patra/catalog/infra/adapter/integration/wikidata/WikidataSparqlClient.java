package com.patra.catalog.infra.adapter.integration.wikidata;

import com.patra.catalog.domain.model.vo.venue.VenueWikidataEnrichment;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// Wikidata SPARQL 查询客户端。
///
/// 通过 Wikidata SPARQL 端点批量查询期刊的富化数据（中文标题 + 封面图片 + 官方网站）。
/// 使用 ISSN-L（P7363）和 ISSN（P236）作为匹配属性，
/// 支持 zh/zh-hans/zh-hant 三种中文标签，按优先级选择最佳标签，
/// 同时通过 P18 属性获取封面图片的 Wikimedia Commons URL，
/// 通过 P856 属性获取官方网站 URL。
///
/// **SPARQL 查询策略**：
///
/// - 使用 `VALUES` 子句批量查询，减少 API 调用次数
/// - 同时查询 P7363（ISSN-L）和 P236（ISSN）两个属性，覆盖更多条目
/// - 中文标题（`OPTIONAL { rdfs:label }`）、封面图片（`OPTIONAL { P18 }`）、官方网站（`OPTIONAL { P856 }`）同时获取
/// - `FILTER(BOUND(?label) || BOUND(?image) || BOUND(?homepage))` 过滤无富化数据的结果行
///
/// **中文标签优先级**：zh > zh-hans > zh-hant
///
/// **注意**：此类非 Spring 组件，由 Boot 层 `WikidataConfiguration` 创建为 Bean。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class WikidataSparqlClient {

  /// ISSN-L 格式验证正则：4位数字-3位数字+1位校验位（数字或 X）。
  ///
  /// 防止 SPARQL 注入：只有合法格式的 ISSN-L 才会被拼入查询。
  static final Pattern ISSN_PATTERN = Pattern.compile("^\\d{4}-\\d{3}[\\dXx]$");

  /// 中文标签优先级：简体中文 > 简体（非标准）> 繁体中文
  static final List<String> LANG_PRIORITY = List.of("zh", "zh-hans", "zh-hant");

  /// SPARQL 查询模板。
  ///
  /// - `%s` 占位符替换为 VALUES 子句（如 `"0028-0836" "0140-6736"`）
  /// - 同时查询 P7363（ISSN-L）和 P236（ISSN）
  /// - OPTIONAL 获取中文标签（zh/zh-hans/zh-hant）、封面图片（P18）、官方网站（P856）
  /// - `FILTER(BOUND(?label) || BOUND(?image) || BOUND(?homepage))` 只保留有富化数据的条目
  static final String SPARQL_TEMPLATE =
      """
      SELECT ?issnl ?label ?image ?homepage WHERE {
        VALUES ?issnl { %s }
        {
          ?item wdt:P7363 ?issnl .
        } UNION {
          ?item wdt:P236 ?issnl .
        }
        OPTIONAL {
          ?item rdfs:label ?label .
          FILTER(LANG(?label) IN ("zh", "zh-hans", "zh-hant"))
        }
        OPTIONAL { ?item wdt:P18 ?image. }
        OPTIONAL { ?item wdt:P856 ?homepage. }
        FILTER(BOUND(?label) || BOUND(?image) || BOUND(?homepage))
      }
      """;

  /// 默认最大重试次数
  private static final int DEFAULT_MAX_RETRIES = 2;

  /// 默认重试基础延迟（指数退避：2s → 4s）
  private static final Duration DEFAULT_RETRY_BASE_DELAY = Duration.ofSeconds(2);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final int maxRetries;
  private final Duration retryBaseDelay;

  /// 生产构造器：使用默认重试配置（最多 2 次重试，指数退避 2s → 4s）。
  ///
  /// @param restClient Wikidata SPARQL 端点专用 RestClient
  /// @param objectMapper Jackson ObjectMapper
  public WikidataSparqlClient(RestClient restClient, ObjectMapper objectMapper) {
    this(restClient, objectMapper, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_BASE_DELAY);
  }

  /// 可配置重试参数的构造器（包内可见，供测试使用）。
  ///
  /// @param restClient Wikidata SPARQL 端点专用 RestClient
  /// @param objectMapper Jackson ObjectMapper
  /// @param maxRetries 最大重试次数
  /// @param retryBaseDelay 重试基础延迟（每次重试翻倍）
  WikidataSparqlClient(
      RestClient restClient, ObjectMapper objectMapper, int maxRetries, Duration retryBaseDelay) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.maxRetries = maxRetries;
    this.retryBaseDelay = retryBaseDelay;
  }

  /// 批量查询 ISSN-L 对应的 Wikidata 富化数据（中文标题 + 封面图片 + 官方网站）。
  ///
  /// 输入集合中的非法 ISSN-L 格式会被静默过滤（防止 SPARQL 注入）。
  ///
  /// @param issnLs ISSN-L 集合
  /// @return ISSN-L → 富化数据映射；查询失败时返回空 Map
  public Map<String, VenueWikidataEnrichment> queryEnrichmentData(Set<String> issnLs) {
    if (issnLs == null || issnLs.isEmpty()) {
      return Map.of();
    }

    // 过滤非法 ISSN-L 格式，防止 SPARQL 注入
    Set<String> validIssnLs =
        issnLs.stream()
            .filter(issn -> ISSN_PATTERN.matcher(issn).matches())
            .collect(Collectors.toSet());

    if (validIssnLs.isEmpty()) {
      log.debug("过滤后无有效 ISSN-L，跳过 Wikidata 查询");
      return Map.of();
    }

    String sparql = buildSparqlQuery(validIssnLs);
    MultiValueMap<String, String> formData = buildFormData(sparql);
    log.debug("Wikidata SPARQL 富化查询 {} 个 ISSN-L", validIssnLs.size());

    Exception lastException = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      if (attempt > 0) {
        Duration delay = retryBaseDelay.multipliedBy(1L << (attempt - 1));
        log.debug("Wikidata SPARQL 查询第 {} 次重试，等待 {}ms", attempt, delay.toMillis());
        try {
          Thread.sleep(delay.toMillis());
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          log.warn("Wikidata SPARQL 重试等待被中断");
          return Map.of();
        }
      }

      try {
        String responseBody = executeSparqlQuery(formData);
        return parseSparqlResponse(responseBody);
      } catch (Exception ex) {
        lastException = ex;
        if (attempt < maxRetries) {
          log.debug("Wikidata SPARQL 查询失败（第 {} 次），将重试：{}", attempt + 1, ex.getMessage());
        }
      }
    }

    log.warn(
        "Wikidata SPARQL 富化查询在 {} 次尝试后仍失败: {}",
        maxRetries + 1,
        lastException != null ? lastException.getMessage() : "unknown");
    return Map.of();
  }

  /// 执行 SPARQL 查询 HTTP 调用。
  ///
  /// 包内可见，供测试 spy 覆盖。
  ///
  /// @param formData POST 表单数据
  /// @return 响应 JSON 字符串
  String executeSparqlQuery(MultiValueMap<String, String> formData) {
    return restClient
        .post()
        .uri("/sparql")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .accept(MediaType.APPLICATION_JSON)
        .body(formData)
        .retrieve()
        .body(String.class);
  }

  /// 构建 SPARQL 查询字符串。
  ///
  /// 将已验证的 ISSN-L 集合格式化为 VALUES 子句并插入模板。
  /// 调用方需确保输入已通过 ISSN-L 格式验证。
  String buildSparqlQuery(Set<String> issnLs) {
    String valuesClause =
        issnLs.stream().map(issn -> "\"" + issn + "\"").collect(Collectors.joining(" "));
    return SPARQL_TEMPLATE.formatted(valuesClause);
  }

  /// 构建 SPARQL POST 请求的表单数据。
  ///
  /// @param sparql SPARQL 查询字符串
  /// @return 包含 `query` 参数的表单数据
  private MultiValueMap<String, String> buildFormData(String sparql) {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("query", sparql);
    return formData;
  }

  /// 解析 SPARQL JSON 响应，返回 ISSN-L → 富化数据映射。
  ///
  /// 响应格式（每行可包含 label、image、homepage，均为可选）：
  ///
  /// ```json
  /// { "results": { "bindings": [
  ///   {
  ///     "issnl": {"value": "0028-0836"},
  ///     "label": {"value": "自然", "xml:lang": "zh"},
  ///     "image": {"value":
  // "http://commons.wikimedia.org/wiki/Special:FilePath/Nature_magazine.jpg"},
  ///     "homepage": {"value": "https://www.nature.com"}
  ///   }
  /// ]}}
  /// ```
  ///
  /// **解析策略**：
  ///
  /// - 同一 ISSN-L 可能有多行（中文标签 × 图片 × 官方网站的笛卡尔积），需聚合
  /// - 中文标题按优先级（zh > zh-hans > zh-hant）选择最佳值
  /// - 图片 URL 和官方网站 URL 各取第一个非空值（putIfAbsent 语义）
  Map<String, VenueWikidataEnrichment> parseSparqlResponse(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }

    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode bindings = root.path("results").path("bindings");

      if (bindings.isMissingNode() || !bindings.isArray()) {
        return Map.of();
      }

      // 收集每个 ISSN-L 的所有语言标签、图片 URL 和官方网站 URL
      Map<String, Map<String, String>> issnlToLangLabels = new HashMap<>();
      Map<String, String> issnlToImageUrl = new HashMap<>();
      Map<String, String> issnlToHomepageUrl = new HashMap<>();

      for (JsonNode binding : bindings) {
        String issnl = binding.path("issnl").path("value").asText("");
        if (issnl.isEmpty()) {
          continue;
        }

        // 收集中文标签
        JsonNode labelNode = binding.path("label");
        if (!labelNode.isMissingNode()) {
          String label = labelNode.path("value").asText("");
          String lang = labelNode.path("xml:lang").asText("");
          if (!label.isEmpty() && !lang.isEmpty()) {
            issnlToLangLabels.computeIfAbsent(issnl, k -> new HashMap<>()).put(lang, label);
          }
        }

        // 收集封面图片 URL（取第一个非空值）
        JsonNode imageNode = binding.path("image");
        if (!imageNode.isMissingNode()) {
          String imageUrl = imageNode.path("value").asText("");
          if (!imageUrl.isEmpty()) {
            issnlToImageUrl.putIfAbsent(issnl, imageUrl);
          }
        }

        // 收集官方网站 URL（取第一个非空值）
        JsonNode homepageNode = binding.path("homepage");
        if (!homepageNode.isMissingNode()) {
          String homepageUrl = homepageNode.path("value").asText("");
          if (!homepageUrl.isEmpty()) {
            issnlToHomepageUrl.putIfAbsent(issnl, homepageUrl);
          }
        }
      }

      // 合并：按优先级选最佳中文标签 + 取收集到的图片和官方网站 URL
      Map<String, VenueWikidataEnrichment> result = new HashMap<>();
      Set<String> allIssnLs = new HashSet<>();
      allIssnLs.addAll(issnlToLangLabels.keySet());
      allIssnLs.addAll(issnlToImageUrl.keySet());
      allIssnLs.addAll(issnlToHomepageUrl.keySet());

      for (String issnl : allIssnLs) {
        String titleZh = selectBestChineseLabel(issnlToLangLabels.get(issnl));
        String imageUrl = issnlToImageUrl.get(issnl);
        String homepageUrl = issnlToHomepageUrl.get(issnl);
        result.put(issnl, VenueWikidataEnrichment.of(titleZh, imageUrl, homepageUrl));
      }

      log.debug(
          "Wikidata 富化查询结果：请求处理 {} 个 ISSN-L，中文标题 {} 个，封面图片 {} 个，官方网站 {} 个",
          allIssnLs.size(),
          issnlToLangLabels.size(),
          issnlToImageUrl.size(),
          issnlToHomepageUrl.size());
      return result;

    } catch (Exception e) {
      log.warn("Wikidata SPARQL 响应解析失败: {}", e.getMessage());
      return Map.of();
    }
  }

  /// 按优先级（zh > zh-hans > zh-hant）选择最佳中文标签。
  ///
  /// @param langLabels 语言 → 标签映射（可为 null）
  /// @return 最佳中文标签，如果无标签则返回 null
  private String selectBestChineseLabel(Map<String, String> langLabels) {
    if (langLabels == null || langLabels.isEmpty()) {
      return null;
    }
    for (String lang : LANG_PRIORITY) {
      if (langLabels.containsKey(lang)) {
        return langLabels.get(lang);
      }
    }
    return null;
  }
}
