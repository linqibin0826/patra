package com.patra.catalog.infra.adapter.integration.wikidata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// Wikidata SPARQL 查询客户端。
///
/// 通过 Wikidata SPARQL 端点批量查询期刊的中文标题。
/// 使用 ISSN-L（P7363）和 ISSN（P236）作为匹配属性，
/// 支持 zh/zh-hans/zh-hant 三种中文标签，按优先级选择最佳标签。
///
/// **SPARQL 查询策略**：
///
/// - 使用 `VALUES` 子句批量查询，减少 API 调用次数
/// - 同时查询 P7363（ISSN-L）和 P236（ISSN）两个属性，覆盖更多条目
/// - 通过 `FILTER(LANG(...))` 仅获取中文标签
///
/// **中文标签优先级**：zh > zh-hans > zh-hant
///
/// **注意**：此类非 Spring 组件，由 Boot 层 `WikidataConfiguration` 创建为 Bean。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
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
  /// - 仅获取中文标签（zh/zh-hans/zh-hant）
  static final String SPARQL_TEMPLATE =
      """
      SELECT ?issnl ?label WHERE {
        VALUES ?issnl { %s }
        {
          ?item wdt:P7363 ?issnl .
        } UNION {
          ?item wdt:P236 ?issnl .
        }
        ?item rdfs:label ?label .
        FILTER(LANG(?label) IN ("zh", "zh-hans", "zh-hant"))
      }
      """;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  /// 批量查询 ISSN-L 对应的中文标题。
  ///
  /// 输入集合中的非法 ISSN-L 格式会被静默过滤（防止 SPARQL 注入）。
  ///
  /// @param issnLs ISSN-L 集合
  /// @return ISSN-L → 中文标题映射；查询失败时返回空 Map
  public Map<String, String> queryChineseTitles(Set<String> issnLs) {
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

    try {
      String sparql = buildSparqlQuery(validIssnLs);
      log.debug("Wikidata SPARQL 查询 {} 个 ISSN-L", validIssnLs.size());

      String responseBody =
          restClient
              .post()
              .uri("/sparql")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(buildFormData(sparql))
              .retrieve()
              .body(String.class);

      return parseSparqlResponse(responseBody);

    } catch (Exception ex) {
      log.warn("Wikidata SPARQL 查询失败: {}", ex.getMessage());
      return Map.of();
    }
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

  /// 构建 POST 表单数据。
  private MultiValueMap<String, String> buildFormData(String sparql) {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("query", sparql);
    return formData;
  }

  /// 解析 SPARQL JSON 响应。
  ///
  /// 响应格式：
  ///
  /// ```json
  /// { "results": { "bindings": [
  ///   { "issnl": {"value": "0028-0836"}, "label": {"value": "自然", "xml:lang": "zh"} }
  /// ]}}
  /// ```
  ///
  /// 同一 ISSN-L 可能有多个语言标签，按优先级（zh > zh-hans > zh-hant）选择。
  Map<String, String> parseSparqlResponse(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }

    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode bindings = root.path("results").path("bindings");

      if (bindings.isMissingNode() || !bindings.isArray()) {
        return Map.of();
      }

      // 收集每个 ISSN-L 的所有语言标签
      Map<String, Map<String, String>> issnlToLangLabels = new HashMap<>();
      for (JsonNode binding : bindings) {
        String issnl = binding.path("issnl").path("value").asText("");
        String label = binding.path("label").path("value").asText("");
        String lang = binding.path("label").path("xml:lang").asText("");

        if (!issnl.isEmpty() && !label.isEmpty() && !lang.isEmpty()) {
          issnlToLangLabels.computeIfAbsent(issnl, k -> new HashMap<>()).put(lang, label);
        }
      }

      // 按优先级选择最佳中文标签
      Map<String, String> result = new HashMap<>();
      for (var entry : issnlToLangLabels.entrySet()) {
        Map<String, String> langLabels = entry.getValue();
        for (String lang : LANG_PRIORITY) {
          if (langLabels.containsKey(lang)) {
            result.put(entry.getKey(), langLabels.get(lang));
            break;
          }
        }
      }

      log.debug(
          "Wikidata 查询结果：请求 {} 个 ISSN-L，匹配 {} 个中文标题", issnlToLangLabels.size(), result.size());
      return result;

    } catch (Exception e) {
      log.warn("Wikidata SPARQL 响应解析失败: {}", e.getMessage());
      return Map.of();
    }
  }
}
